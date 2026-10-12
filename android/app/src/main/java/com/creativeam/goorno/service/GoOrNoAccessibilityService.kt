package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.MainActivity
import com.creativeam.goorno.R
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.diagnostics.AccessibilitySnapshot
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço principal de leitura automática de ofertas (Android AccessibilityService) v2.3.0.
 * - Executa TANTO por eventos (onAccessibilityEvent) QUANTO por varredura ativa contínua a cada 1 segundo,
 *   garantindo a leitura imediata mesmo quando a tela de ofertas da 99 já está aberta e parada.
 * - Inspeciona TODAS as janelas visíveis (windows + rootInActiveWindow) e junta textos que estão na
 *   mesma linha horizontal (ex: "R$" + "8,10" + "⚡1,1x" em TextViews separados na 99).
 * - Mantém a Barrinha de Status no Topo da tela sempre atualizada ("🟢 GoOrNo ATIVO · Lendo 99").
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var lastBatchSignature: String = ""
    private var lastBatchTimestampMillis: Long = 0L
    private var lastScanTimeMillis: Long = 0L

    private data class PositionedText(
        val text: String,
        val topY: Int,
        val leftX: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeDiagnosticsBus.setAccessibilityConnected(true)
        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
        showActiveStatusNotification()

        val app = applicationContext as? GoOrNoApplication
        app?.overlayPanel?.showOrUpdateTopStatusBar(accessibilityEnabled = true)

        startActivePollingLoop()
    }

    private fun startActivePollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    scanCurrentScreen(eventPkg = null, forceThrottleBypass = true)
                } catch (_: Exception) {
                }
                delay(1000L)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evtPkg = event?.packageName?.toString()
        scanCurrentScreen(eventPkg = evtPkg, forceThrottleBypass = false)
    }

    private fun scanCurrentScreen(eventPkg: String?, forceThrottleBypass: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceThrottleBypass && now - lastScanTimeMillis < 250L) return
        lastScanTimeMillis = now

        val candidateWindows = mutableListOf<Pair<String, AccessibilityNodeInfo>>()

        // 1. Coleta TODAS as janelas visíveis na tela (ignorando apenas o próprio GoOrNo e SystemUI)
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (win in allWindows) {
                    val root = win.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui") continue
                    candidateWindows.add(Pair(pkg, root))
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback/complemento com rootInActiveWindow
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val activePkg = activeRoot.packageName?.toString() ?: eventPkg ?: ""
                if (activePkg.isNotBlank() && activePkg != packageName && activePkg != "com.android.systemui") {
                    if (candidateWindows.none { it.second == activeRoot }) {
                        candidateWindows.add(0, Pair(activePkg, activeRoot))
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (candidateWindows.isEmpty()) return

        // Prioriza janelas da Uber ou 99 se presentes, mas coleta nós de TODAS as janelas do aplicativo ativo
        val rideWindow = candidateWindows.firstOrNull { (pkg, _) ->
            SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN
        }
        val targetPkg = rideWindow?.first ?: candidateWindows.first().first

        val positionedNodes = mutableListOf<PositionedText>()
        for ((pkg, rootNode) in candidateWindows) {
            // Se identificamos a Uber ou 99, lemos todas as janelas/popups dela; caso contrário lemos todas as janelas visíveis
            if (rideWindow == null || pkg == targetPkg || SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN) {
                collectPositionedTexts(rootNode, positionedNodes, depth = 0)
            }
        }

        if (positionedNodes.isEmpty()) return

        // Agrupa nós que estão na mesma linha visual (mesma faixa de coordenada Y) da esquerda para a direita!
        // Isso resolve quando a 99 coloca "R$" em um TextView e "8,10" em outro TextView ao lado.
        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)

        val detectedApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedTexts)

        // Atualiza a barrinha do topo com o status ao vivo
        val app = applicationContext as? GoOrNoApplication
        if (detectedApp == SourceApp.NINETY_NINE_DRIVER) {
            app?.overlayPanel?.showOrUpdateTopStatusBar(true, "Lendo 99 (${normalizedTexts.size} itens)")
        } else if (detectedApp == SourceApp.UBER_DRIVER) {
            app?.overlayPanel?.showOrUpdateTopStatusBar(true, "Lendo Uber (${normalizedTexts.size} itens)")
        } else {
            app?.overlayPanel?.showOrUpdateTopStatusBar(true, "Monitorando Uber/99")
        }

        // Atualiza o Inspetor na Tela 9 para diagnóstico em tempo real
        RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
            AccessibilitySnapshot(
                timestampMillis = now,
                packageName = targetPkg,
                detectedApp = detectedApp,
                nodeCount = normalizedTexts.size,
                extractedTexts = normalizedTexts.take(90)
            )
        )

        if (detectedApp != SourceApp.UBER_DRIVER && detectedApp != SourceApp.NINETY_NINE_DRIVER) {
            return
        }

        // Extrai 1 ou MÚLTIPLAS ofertas visíveis na tela
        val parsedOffers = when (detectedApp) {
            SourceApp.UBER_DRIVER -> UberOfferReader.parseMultipleOffersFromScreen(
                rawNodes = normalizedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            SourceApp.NINETY_NINE_DRIVER -> NinetyNineOfferReader.parseMultipleOffersFromScreen(
                rawNodes = normalizedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            else -> emptyList()
        }

        if (parsedOffers.isEmpty()) return

        // Verifica deduplicação do lote de ofertas na tela (janela de 8 segundos para mesma oferta)
        val currentBatchSig = parsedOffers.joinToString("||") { it.dedupFingerprint }
        if (currentBatchSig == lastBatchSignature && (now - lastBatchTimestampMillis) < 8_000L) {
            return
        }
        lastBatchSignature = currentBatchSig
        lastBatchTimestampMillis = now

        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

        if (app == null) return
        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluations = parsedOffers.map { offer ->
                FinancialEngine.evaluate(offer, config)
            }

            // Publica a primeira oferta no card principal e mostra TODAS na janelinha flutuante
            RuntimeDiagnosticsBus.publishEvaluation(evaluations.first())
            app.overlayPanel.applyConfig(config)
            app.overlayPanel.showEvaluations(evaluations)

            // Grava todas as ofertas reais detectadas no banco local Room
            if (config.saveHistoryEnabled) {
                for (evaluation in evaluations) {
                    val offer = evaluation.offer
                    if (!offer.isSimulated && offer.offeredPrice != null) {
                        val entity = RideOfferEntity(
                            id = offer.offerId,
                            timestampMillis = offer.timestampMillis,
                            sourceApp = offer.sourceApp.displayName,
                            captureMethod = offer.captureMethod.label,
                            rideCategory = offer.rideCategory,
                            offeredPrice = offer.offeredPrice ?: 0.0,
                            actualReceivedPrice = null,
                            pickupDistanceKm = offer.pickupDistanceKm,
                            tripDistanceKm = offer.tripDistanceKm,
                            totalDistanceKm = evaluation.totalConsideredDistanceKm,
                            pickupDurationMin = offer.pickupDurationMin,
                            tripDurationMin = offer.tripDurationMin,
                            totalDurationMin = evaluation.totalConsideredDurationMin,
                            grossPerKm = evaluation.grossPerKm,
                            grossPerHour = evaluation.grossPerHour,
                            estimatedTotalCost = evaluation.estimatedTotalCost,
                            estimatedNetProfit = evaluation.estimatedNetProfit,
                            netProfitPerKm = evaluation.netProfitPerKm,
                            netProfitPerHour = evaluation.netProfitPerHour,
                            verdict = evaluation.verdict.name,
                            reasonsSummary = (evaluation.reasons + evaluation.warnings).joinToString(" | "),
                            originAddress = offer.originAddress,
                            destinationAddress = offer.destinationAddress,
                            confidenceScore = offer.confidenceScore,
                            isPartialReading = !evaluation.isAnalysisComplete,
                            isSimulated = false
                        )
                        app.database.rideOfferDao().insertOffer(entity)
                    }
                }
            }
        }
    }

    /**
     * Junta nós de texto que estão alinhados na mesma linha horizontal (diferença de Y <= 22px)
     * e também preserva as linhas individuais para máxima precisão.
     */
    private fun mergeSameRowNodes(nodes: List<PositionedText>): List<String> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedWith(compareBy<PositionedText>({ it.topY }, { it.leftX }))

        val rows = mutableListOf<MutableList<PositionedText>>()
        for (item in sorted) {
            val lastRow = rows.lastOrNull()
            if (lastRow != null && kotlin.math.abs(lastRow.first().topY - item.topY) <= 22) {
                if (lastRow.none { it.text == item.text }) {
                    lastRow.add(item)
                }
            } else {
                rows.add(mutableListOf(item))
            }
        }

        val output = mutableListOf<String>()
        for (row in rows) {
            row.sortBy { it.leftX }
            val joinedRow = row.joinToString(" ") { it.text }.trim()
            if (joinedRow.isNotEmpty()) {
                output.add(joinedRow)
            }
        }
        return output.distinct()
    }

    private fun collectPositionedTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<PositionedText>,
        depth: Int
    ) {
        if (node == null || depth > 35 || output.size >= 220) return
        val rect = Rect()
        try {
            node.getBoundsInScreen(rect)
        } catch (_: Exception) {
        }

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
            output.add(PositionedText(txt, rect.top, rect.left))
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            if (output.none { it.text == desc && kotlin.math.abs(it.topY - rect.top) <= 10 }) {
                output.add(PositionedText(desc, rect.top, rect.left))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                if (output.none { it.text == hint }) {
                    output.add(PositionedText(hint, rect.top, rect.left))
                }
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectPositionedTexts(child, output, depth + 1)
            }
        }
    }

    private fun showActiveStatusNotification() {
        try {
            val channelId = "goorno_active_bar_v2"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "GoOrNo — Copiloto Ativo no Topo",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    description = "Mostra na barra superior do Android que o GoOrNo está ativo"
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🟢 GoOrNo ATIVO — Monitorando Uber e 99")
                .setContentText("Lendo chamadas automaticamente em segundo plano.")
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(STATUS_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    private fun cancelActiveStatusNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(STATUS_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.showOrUpdateTopStatusBar(false)
        cancelActiveStatusNotification()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.showOrUpdateTopStatusBar(false)
        cancelActiveStatusNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val STATUS_NOTIFICATION_ID = 4102
    }
}
