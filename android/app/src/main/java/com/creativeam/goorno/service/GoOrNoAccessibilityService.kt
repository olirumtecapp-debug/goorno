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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço principal de leitura automática de ofertas (Android AccessibilityService).
 * - Inspeciona TODAS as janelas interativas visíveis (windows + rootInActiveWindow),
 *   garantindo que a janela da 99 ou da Uber seja lida mesmo quando o painel flutuante está aberto.
 * - Ordena os textos de cima para baixo (coordenada Y na tela) para preservar a ordem exata de
 *   múltiplas viagens na lista de ofertas da 99 ou no Radar da Uber.
 * - Exibe notificação na barra de status indicando claramente que o GoOrNo está ATIVO.
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBatchSignature: String = ""
    private var lastBatchTimestampMillis: Long = 0L
    private var lastEventTimeMillis: Long = 0L

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastEventTimeMillis < 220L) return
        lastEventTimeMillis = now

        val candidateWindows = mutableListOf<Pair<String, AccessibilityNodeInfo>>()

        // 1. Coleta todas as janelas interativas na tela (ignorando a janela do próprio GoOrNo e barra de sistema)
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

        // 2. Fallback para rootInActiveWindow
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val activePkg = activeRoot.packageName?.toString() ?: event?.packageName?.toString() ?: ""
            if (activePkg.isNotBlank() && activePkg != packageName && activePkg != "com.android.systemui") {
                if (candidateWindows.none { it.first == activePkg }) {
                    candidateWindows.add(0, Pair(activePkg, activeRoot))
                }
            }
        }

        if (candidateWindows.isEmpty()) return

        // Prioriza a janela da Uber ou 99 se estiver entre as janelas abertas
        val bestWindow = candidateWindows.firstOrNull { (pkg, _) ->
            SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN
        } ?: candidateWindows.first()

        val targetPkg = bestWindow.first
        val positionedNodes = mutableListOf<PositionedText>()
        collectPositionedTexts(bestWindow.second, positionedNodes, depth = 0)

        // Ordena de cima para baixo (topY) e da esquerda para a direita (leftX)
        val sortedTexts = positionedNodes
            .sortedWith(compareBy<PositionedText>({ it.topY / 16 }, { it.leftX }))
            .map { it.text }
            .distinct()

        val detectedApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, sortedTexts)

        // Atualiza o Inspetor na Tela 9 para diagnóstico em tempo real
        RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
            AccessibilitySnapshot(
                timestampMillis = now,
                packageName = targetPkg,
                detectedApp = detectedApp,
                nodeCount = sortedTexts.size,
                extractedTexts = sortedTexts.take(90)
            )
        )

        if (detectedApp != SourceApp.UBER_DRIVER && detectedApp != SourceApp.NINETY_NINE_DRIVER) {
            return
        }

        // Extrai 1 ou MÚLTIPLAS ofertas visíveis na tela
        val parsedOffers = when (detectedApp) {
            SourceApp.UBER_DRIVER -> UberOfferReader.parseMultipleOffersFromScreen(
                rawNodes = sortedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            SourceApp.NINETY_NINE_DRIVER -> NinetyNineOfferReader.parseMultipleOffersFromScreen(
                rawNodes = sortedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            else -> emptyList()
        }

        if (parsedOffers.isEmpty()) return

        // Verifica deduplicação do lote de ofertas na tela
        val currentBatchSig = parsedOffers.joinToString("||") { it.dedupFingerprint }
        if (currentBatchSig == lastBatchSignature && (now - lastBatchTimestampMillis) < 10_000L) {
            return
        }
        lastBatchSignature = currentBatchSig
        lastBatchTimestampMillis = now

        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

        val app = applicationContext as? GoOrNoApplication ?: return
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

    private fun collectPositionedTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<PositionedText>,
        depth: Int
    ) {
        if (node == null || depth > 30 || output.size >= 150) return
        val rect = Rect()
        try {
            node.getBoundsInScreen(rect)
        } catch (_: Exception) {
        }

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
            output.add(PositionedText(txt, rect.top, rect.left))
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            if (output.none { it.text == desc }) {
                output.add(PositionedText(desc, rect.top, rect.left))
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
            val channelId = "goorno_status_channel"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "GoOrNo — Status do Copiloto",
                    NotificationManager.IMPORTANCE_LOW
                )
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
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🟢 GoOrNo ATIVO — Monitorando Uber e 99")
                .setContentText("A janela flutuante abrirá automaticamente quando tocar uma chamada.")
                .setOngoing(true)
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
        cancelActiveStatusNotification()
    }

    override fun onDestroy() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        cancelActiveStatusNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val STATUS_NOTIFICATION_ID = 4100
    }
}
