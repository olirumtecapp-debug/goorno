package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
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
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço principal de leitura automática de ofertas (Android AccessibilityService) v2.5.0.
 *
 * 1. Varredura dupla:
 *    - Leitura imediata da Árvore de Acessibilidade (windows + rootInActiveWindow).
 *    - Quando a 99 ou Uber esconde os textos da árvore (retornando apenas 1 a 3 itens como nas telas baseadas em Canvas/Flutter),
 *      aciona AUTOMATICAMENTE o `takeScreenshot(Display.DEFAULT_DISPLAY)` nativo do Android 11+ com Google ML Kit OCR local,
 *      SEM pedir permissão de gravação de tela e SEM precisar que o motorista clique na barrinha!
 * 2. Quando o motorista clica no ícone do "mapinha" (ou em qualquer card de oferta na lista de Solicitações)
 *    para ver a viagem em detalhes, o clique e a mudança de tela limpam a trava de repetição e abrem
 *    a janelinha automaticamente com a leitura completa da viagem selecionada.
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var lastBatchSignature: String = ""
    private var lastBatchTimestampMillis: Long = 0L
    private var lastScreenContextMode: String = ""
    private var lastScanTimeMillis: Long = 0L
    private var lastAutoScreenshotTimeMillis: Long = 0L
    private var isTakingAutoScreenshot: Boolean = false
    private var forceReopenOnNextOffer: Boolean = false

    private val mlKitRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private data class PositionedText(
        val text: String,
        val topY: Int,
        val leftX: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeDiagnosticsBus.setAccessibilityConnected(true)
        val app = applicationContext as? GoOrNoApplication
        serviceScope.launch {
            val cfg = app?.preferencesRepository?.getConfigOnce()
            val enabled = cfg?.copilotMonitoringEnabled ?: true
            app?.overlayPanel?.setMonitoringEnabled(enabled, accessibilityEnabled = true)
            if (enabled) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                showActiveStatusNotification()
            } else {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
                cancelActiveStatusNotification()
            }
        }

        startActivePollingLoop()
    }

    private fun startActivePollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val app = applicationContext as? GoOrNoApplication
                    if (app?.overlayPanel?.isCopilotMonitoringEnabled() == true) {
                        scanCurrentScreen(eventPkg = null, forceThrottleBypass = true)
                    }
                } catch (_: Exception) {
                }
                delay(900L)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = applicationContext as? GoOrNoApplication
        if (app?.overlayPanel?.isCopilotMonitoringEnabled() == false) {
            return
        }

        val evtType = event?.eventType ?: 0
        val evtPkg = event?.packageName?.toString()

        // Quando o motorista clica no ícone do mapinha na 99 ou muda da lista para o mapa detalhado,
        // liberamos a reabertura imediata da janelinha mesmo que seja a mesma corrida!
        if (evtType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            evtType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            forceReopenOnNextOffer = true
            lastBatchTimestampMillis = 0L
        }

        scanCurrentScreen(eventPkg = evtPkg, forceThrottleBypass = forceReopenOnNextOffer)
    }

    private fun scanCurrentScreen(eventPkg: String?, forceThrottleBypass: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceThrottleBypass && now - lastScanTimeMillis < 220L) return
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

        val rideWindow = candidateWindows.firstOrNull { (pkg, _) ->
            SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN
        }
        val targetPkg = rideWindow?.first ?: candidateWindows.first().first

        val positionedNodes = mutableListOf<PositionedText>()
        for ((pkg, rootNode) in candidateWindows) {
            if (rideWindow == null || pkg == targetPkg || SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN) {
                collectPositionedTexts(rootNode, positionedNodes, depth = 0)
            }
        }

        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)
        val detectedApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedTexts)

        val app = applicationContext as? GoOrNoApplication

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

        val isRideApp = detectedApp == SourceApp.UBER_DRIVER ||
            detectedApp == SourceApp.NINETY_NINE_DRIVER ||
            SourceApp.fromPackageName(targetPkg) != SourceApp.UNKNOWN

        if (!isRideApp) {
            app?.overlayPanel?.showOrUpdateTopStatusBar(true, "Monitorando Uber/99")
            return
        }

        val effectiveApp = if (detectedApp != SourceApp.UNKNOWN) {
            detectedApp
        } else {
            SourceApp.fromPackageName(targetPkg)
        }

        // Tenta extrair 1 ou MÚLTIPLAS ofertas diretamente pelos nós de Acessibilidade
        val parsedOffers = extractOffersForApp(effectiveApp, normalizedTexts, CaptureMethod.ACCESSIBILITY, now)

        if (parsedOffers.isNotEmpty()) {
            val appShort = if (effectiveApp == SourceApp.NINETY_NINE_DRIVER) "99" else "Uber"
            app?.overlayPanel?.showOrUpdateTopStatusBar(
                true,
                "Lendo $appShort (${parsedOffers.size} ${if (parsedOffers.size > 1) "ofertas" else "oferta"})"
            )
            processAndShowOffers(parsedOffers, normalizedTexts, now)
        } else {
            // A 99 ou Uber está aberta na tela (ex: retornou só "2 itens" porque os cards estão desenhados sem texto acessível)!
            // Acionamos AUTOMATICAMENTE a leitura visual da tela (takeScreenshot + ML Kit OCR) sem precisar tocar em nada!
            val appShort = if (effectiveApp == SourceApp.NINETY_NINE_DRIVER) "99" else "Uber"
            app?.overlayPanel?.showOrUpdateTopStatusBar(true, "Lendo $appShort")
            triggerAutomaticAccessibilityOcrIfNeeded(effectiveApp, targetPkg, now)
        }
    }

    private fun extractOffersForApp(
        appType: SourceApp,
        lines: List<String>,
        method: CaptureMethod,
        timestampMillis: Long
    ): List<StandardRideOffer> {
        if (lines.isEmpty()) return emptyList()
        return when (appType) {
            SourceApp.UBER_DRIVER -> UberOfferReader.parseMultipleOffersFromScreen(
                rawNodes = lines,
                captureMethod = method,
                timestampMillis = timestampMillis,
                isSimulated = false
            )
            SourceApp.NINETY_NINE_DRIVER -> NinetyNineOfferReader.parseMultipleOffersFromScreen(
                rawNodes = lines,
                captureMethod = method,
                timestampMillis = timestampMillis,
                isSimulated = false
            )
            else -> emptyList()
        }
    }

    /**
     * Quando a 99 Motorista ou Uber Driver esconde os textos da árvore de acessibilidade (mostrando apenas "2 itens"),
     * o GoOrNo tira uma captura silenciosa via AccessibilityService.takeScreenshot (Android 11 / API 30+)
     * e lê todos os preços, km, metros, minutos e endereços via ML Kit OCR local — abrindo a janelinha SOZINHO!
     */
    private fun triggerAutomaticAccessibilityOcrIfNeeded(
        targetApp: SourceApp,
        targetPkg: String,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (isTakingAutoScreenshot) return
        val minInterval = if (forceReopenOnNextOffer) 450L else 1250L
        if (now - lastAutoScreenshotTimeMillis < minInterval) return

        isTakingAutoScreenshot = true
        lastAutoScreenshotTimeMillis = now

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val hwBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val hwBitmap = try {
                            Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        } catch (_: Exception) {
                            null
                        }

                        if (hwBitmap == null) {
                            try {
                                hwBuffer.close()
                            } catch (_: Exception) {
                            }
                            isTakingAutoScreenshot = false
                            return
                        }

                        val softBitmap = try {
                            hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) {
                            null
                        }
                        hwBitmap.recycle()
                        try {
                            hwBuffer.close()
                        } catch (_: Exception) {
                        }

                        if (softBitmap == null) {
                            isTakingAutoScreenshot = false
                            return
                        }

                        // Corta apenas os 7% do topo da tela (onde ficam o relógio do sistema e a barrinha do GoOrNo)
                        // para que o OCR nunca leia o próprio card ou barrinha do GoOrNo, mas leia toda a lista de Solicitações e o card de chamada!
                        val cropTop = (softBitmap.height * 0.07).toInt().coerceAtLeast(1)
                        val cropHeight = (softBitmap.height - cropTop).coerceAtLeast(100)
                        val croppedBitmap = try {
                            Bitmap.createBitmap(softBitmap, 0, cropTop, softBitmap.width, cropHeight)
                        } catch (_: Exception) {
                            softBitmap
                        }
                        if (croppedBitmap != softBitmap) {
                            softBitmap.recycle()
                        }

                        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                        mlKitRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val positionedOcr = mutableListOf<PositionedText>()
                                for (block in visionText.textBlocks) {
                                    for (line in block.lines) {
                                        val box = line.boundingBox
                                        val txt = line.text.trim()
                                        // Ignora textos que pertençam à própria janelinha do GoOrNo se ela já estiver aberta
                                        if (txt.isNotEmpty() &&
                                            !txt.contains("GoOrNo", ignoreCase = true) &&
                                            !txt.contains("COMPENSA", ignoreCase = true) &&
                                            !txt.contains("Modo Simulação", ignoreCase = true) &&
                                            !txt.startsWith("📍 Origem:") &&
                                            !txt.startsWith("🏁 Destino:") &&
                                            !txt.startsWith("Embarque:")
                                        ) {
                                            positionedOcr.add(
                                                PositionedText(
                                                    text = txt,
                                                    topY = box?.top ?: 0,
                                                    leftX = box?.left ?: 0
                                                )
                                            )
                                        }
                                    }
                                }
                                croppedBitmap.recycle()

                                val mergedOcrLines = mergeSameRowNodes(positionedOcr)
                                val normalizedOcr = BrazilianOfferParser.normalizeFragmentedNodes(mergedOcrLines)

                                if (normalizedOcr.isNotEmpty()) {
                                    RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
                                        AccessibilitySnapshot(
                                            timestampMillis = System.currentTimeMillis(),
                                            packageName = "$targetPkg (Leitura Visual Automática)",
                                            detectedApp = targetApp,
                                            nodeCount = normalizedOcr.size,
                                            extractedTexts = normalizedOcr.take(90)
                                        )
                                    )

                                    val ocrOffers = extractOffersForApp(
                                        appType = targetApp,
                                        lines = normalizedOcr,
                                        method = CaptureMethod.ACCESSIBILITY,
                                        timestampMillis = System.currentTimeMillis()
                                    )

                                    if (ocrOffers.isNotEmpty()) {
                                        val app = applicationContext as? GoOrNoApplication
                                        val appShort = if (targetApp == SourceApp.NINETY_NINE_DRIVER) "99" else "Uber"
                                        app?.overlayPanel?.showOrUpdateTopStatusBar(
                                            true,
                                            "Lendo $appShort (${ocrOffers.size} ${if (ocrOffers.size > 1) "ofertas" else "oferta"})"
                                        )
                                        processAndShowOffers(ocrOffers, normalizedOcr, System.currentTimeMillis())
                                    }
                                }
                                isTakingAutoScreenshot = false
                            }
                            .addOnFailureListener {
                                croppedBitmap.recycle()
                                isTakingAutoScreenshot = false
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        isTakingAutoScreenshot = false
                    }
                }
            )
        } catch (_: Exception) {
            isTakingAutoScreenshot = false
        }
    }

    private fun processAndShowOffers(
        parsedOffers: List<StandardRideOffer>,
        screenLines: List<String>,
        now: Long
    ) {
        if (parsedOffers.isEmpty()) return

        // Identifica se estamos na tela de Lista ("Solicitações / Escolha uma") ou na tela de Detalhe do Mapa / Tocando
        val joinedLower = screenLines.joinToString(" ").lowercase()
        val currentScreenMode = when {
            joinedLower.contains("solicitações") || joinedLower.contains("escolha uma") -> "LIST_VIEW"
            else -> "DETAIL_OR_RINGING_VIEW"
        }

        // Quando o motorista clica no mapinha na lista de Solicitações, currentScreenMode muda de LIST_VIEW para DETAIL_OR_RINGING_VIEW!
        val screenModeChanged = currentScreenMode != lastScreenContextMode
        val currentBatchSig = parsedOffers.joinToString("||") { it.dedupFingerprint }

        if (!forceReopenOnNextOffer &&
            !screenModeChanged &&
            currentBatchSig == lastBatchSignature &&
            (now - lastBatchTimestampMillis) < 7_500L
        ) {
            return
        }

        forceReopenOnNextOffer = false
        lastScreenContextMode = currentScreenMode
        lastBatchSignature = currentBatchSig
        lastBatchTimestampMillis = now

        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

        val app = applicationContext as? GoOrNoApplication ?: return
        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluations = parsedOffers.map { offer ->
                FinancialEngine.evaluate(offer, config)
            }

            // Abre a janelinha flutuante AUTOMATICAMENTE com todas as ofertas da tela!
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
     * Junta nós de texto que estão alinhados na mesma linha horizontal (diferença de Y <= 24px).
     */
    private fun mergeSameRowNodes(nodes: List<PositionedText>): List<String> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedWith(compareBy<PositionedText>({ it.topY }, { it.leftX }))

        val rows = mutableListOf<MutableList<PositionedText>>()
        for (item in sorted) {
            val lastRow = rows.lastOrNull()
            if (lastRow != null && kotlin.math.abs(lastRow.first().topY - item.topY) <= 24) {
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

