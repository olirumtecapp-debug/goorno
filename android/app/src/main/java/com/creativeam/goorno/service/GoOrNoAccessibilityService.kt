package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.creativeam.goorno.GoOrNoApplication
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
import java.util.Locale

/**
 * Serviço principal de leitura automática de ofertas (Android AccessibilityService) v2.9.0.
 *
 * Melhorias v2.9.0:
 * 1. Persistência total ao alternar entre WhatsApp / Waze / apps de terceiros e voltar para 99 ou Uber:
 *    - Nunca fica preso no pacote do WhatsApp ("lastKnownForegroundPkg") quando a 99 esconde seus nós de acessibilidade.
 *    - Watchdog automático de 2.4s em `isTakingAutoScreenshot` para nunca travar caso o Android descarte um callback durante transição de janela.
 *    - Limpa automaticamente o cache de ofertas já exibidas (`shownOfferTimestamps`) quando o motorista abre outro app (ex: WhatsApp),
 *      garantindo que ao voltar para a 99 ou Uber o card flutuante abra imediatamente!
 * 2. Atualização da Notificação Superior com a oferta mais completa da tela (preservando Origem, Destino, Tempo e R$/h).
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val shownOfferTimestamps = LinkedHashMap<String, Long>()
    private var lastScreenContextMode: String = ""
    private var lastKnownForegroundPkg: String = ""
    private var lastScanTimeMillis: Long = 0L
    private var lastAutoScreenshotTimeMillis: Long = 0L
    private var lastCardClosedTimeMillis: Long = 0L
    private var isTakingAutoScreenshot: Boolean = false
    private var consecutiveScansWithoutOffers: Int = 0

    private val mlKitRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private data class PositionedText(
        val text: String,
        val topY: Int,
        val leftX: Int,
        val centerY: Int = topY
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeDiagnosticsBus.setAccessibilityConnected(true)
        val app = applicationContext as? GoOrNoApplication

        app?.overlayPanel?.onOfferCardClosed = {
            lastCardClosedTimeMillis = System.currentTimeMillis()
        }

        serviceScope.launch {
            val cfg = app?.preferencesRepository?.getConfigOnce()
            val enabled = cfg?.copilotMonitoringEnabled ?: true
            if (cfg != null) {
                app.overlayPanel.applyConfig(cfg)
            }
            app?.overlayPanel?.setMonitoringEnabled(enabled, accessibilityEnabled = true)
            if (enabled) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                CopilotNotificationReceiver.showActiveNotification(
                    context = this@GoOrNoAccessibilityService,
                    showProfit = cfg?.showProfitOnOverlay,
                    showCost = cfg?.showCostOnOverlay
                )
            } else {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
                CopilotNotificationReceiver.cancelActiveNotification(this@GoOrNoAccessibilityService)
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
                    if (app?.overlayPanel?.isCopilotMonitoringEnabled() == true &&
                        app.overlayPanel.isCardCurrentlyVisible() == false
                    ) {
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

        val evtPkg = event?.packageName?.toString() ?: ""
        if (evtPkg.isNotBlank() &&
            evtPkg != packageName &&
            evtPkg != "com.android.systemui" &&
            !evtPkg.contains("inputmethod", ignoreCase = true) &&
            !evtPkg.contains("keyboard", ignoreCase = true)
        ) {
            // Se o motorista abriu um app terceiro (ex: WhatsApp) que não é Uber nem 99,
            // limpamos o histórico de exibição para que ao voltar para a 99/Uber o card flutuante reabra na hora!
            if (SourceApp.fromPackageName(evtPkg) == SourceApp.UNKNOWN &&
                SourceApp.fromPackageName(lastKnownForegroundPkg) != SourceApp.UNKNOWN
            ) {
                shownOfferTimestamps.clear()
                lastScreenContextMode = ""
            }
            lastKnownForegroundPkg = evtPkg
        }

        if (app?.overlayPanel?.isCardCurrentlyVisible() == true) {
            return
        }

        scanCurrentScreen(eventPkg = evtPkg, forceThrottleBypass = false)
    }

    private fun scanCurrentScreen(eventPkg: String?, forceThrottleBypass: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceThrottleBypass && now - lastScanTimeMillis < 260L) return
        if (now - lastCardClosedTimeMillis < 1100L) return
        lastScanTimeMillis = now

        val candidateWindows = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        var isOurOwnAppVisible = false

        // 1. Coleta todas as janelas visíveis na tela
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (win in allWindows) {
                    val root = win.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg == packageName) {
                        isOurOwnAppVisible = true
                        continue
                    }
                    if (pkg.isBlank() || pkg == "com.android.systemui") continue
                    candidateWindows.add(Pair(pkg, root))
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback/complemento com rootInActiveWindow
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val activePkg = activeRoot.packageName?.toString() ?: eventPkg ?: lastKnownForegroundPkg
                if (activePkg == "com.android.systemui") {
                    // Usuário está com o painel de notificações aberto; não escaneia para não capturar textos do sistema
                    return
                }
                if (activePkg == packageName) {
                    isOurOwnAppVisible = true
                } else if (activePkg.isNotBlank()) {
                    if (candidateWindows.none { it.second == activeRoot }) {
                        candidateWindows.add(0, Pair(activePkg, activeRoot))
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Se o motorista está com a tela do próprio GoOrNo aberta (ajustando configurações), não escaneia
        if (isOurOwnAppVisible && candidateWindows.none { SourceApp.fromPackageName(it.first) != SourceApp.UNKNOWN }) {
            return
        }

        val rideWindow = candidateWindows.firstOrNull { (pkg, _) ->
            SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN
        }
        val targetPkg = rideWindow?.first
            ?: candidateWindows.firstOrNull()?.first
            ?: eventPkg?.takeIf { it.isNotBlank() && it != packageName && it != "com.android.systemui" }
            ?: lastKnownForegroundPkg.takeIf { it.isNotBlank() && it != packageName }
            ?: "com.taxis99.driver"

        val positionedNodes = mutableListOf<PositionedText>()
        for ((pkg, rootNode) in candidateWindows) {
            if (rideWindow == null || pkg == targetPkg || SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN) {
                collectPositionedTexts(rootNode, positionedNodes, depth = 0, requireVisible = true)
            }
        }
        // Fallback caso alguma ROM customize isVisibleToUser como false em toda a janela
        if (positionedNodes.size < 3) {
            positionedNodes.clear()
            for ((pkg, rootNode) in candidateWindows) {
                if (rideWindow == null || pkg == targetPkg || SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN) {
                    collectPositionedTexts(rootNode, positionedNodes, depth = 0, requireVisible = false)
                }
            }
        }

        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)
        val detectedApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedTexts)

        if (normalizedTexts.isNotEmpty()) {
            RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
                AccessibilitySnapshot(
                    timestampMillis = now,
                    packageName = targetPkg,
                    detectedApp = detectedApp,
                    nodeCount = normalizedTexts.size,
                    extractedTexts = normalizedTexts.take(90)
                )
            )
        }

        val isKnownRidePackage = SourceApp.fromPackageName(targetPkg) != SourceApp.UNKNOWN
        val effectiveApp = when {
            detectedApp != SourceApp.UNKNOWN -> detectedApp
            isKnownRidePackage -> SourceApp.fromPackageName(targetPkg)
            else -> SourceApp.UNKNOWN
        }

        // 1º Tentativa: extrair ofertas diretamente pelos nós de Acessibilidade (testando Uber e 99)
        var parsedOffers = if (effectiveApp != SourceApp.UNKNOWN) {
            extractOffersForApp(effectiveApp, normalizedTexts, CaptureMethod.ACCESSIBILITY, now)
        } else {
            emptyList()
        }
        if (parsedOffers.isEmpty() && normalizedTexts.isNotEmpty()) {
            parsedOffers = extractOffersForApp(SourceApp.NINETY_NINE_DRIVER, normalizedTexts, CaptureMethod.ACCESSIBILITY, now)
                .ifEmpty { extractOffersForApp(SourceApp.UBER_DRIVER, normalizedTexts, CaptureMethod.ACCESSIBILITY, now) }
        }

        // Exige que a oferta extraída por Acessibilidade tenha Preço + Distância (km) + Tempo (min).
        // Se no Radar de Viagens da Uber (ou Solicitações da 99) a árvore de Acessibilidade ocultar as distâncias
        // ou os tempos, prosseguimos imediatamente para a 2ª Tentativa (Leitura Visual via takeScreenshot + OCR)!
        val completeAccessibilityOffers = parsedOffers.filter { offer ->
            val km = offer.totalDistanceKmExplicit ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
            val min = offer.totalDurationMinExplicit ?: ((offer.pickupDurationMin ?: 0) + (offer.tripDurationMin ?: 0))
            (offer.offeredPrice ?: 0.0) > 0.0 && km > 0.0 && min > 0
        }

        if (completeAccessibilityOffers.isNotEmpty()) {
            consecutiveScansWithoutOffers = 0
            processAndShowOffers(completeAccessibilityOffers, normalizedTexts, now)
            return
        }

        // 2º Tentativa Automática (Leitura Visual Silenciosa via takeScreenshot + ML Kit OCR):
        // Sempre executada quando não estamos dentro do próprio GoOrNo!
        // Assim, mesmo no Radar de Viagens da Uber ou após alternar com o WhatsApp, o leitor lê a imagem real da tela!
        val fallbackApp = if (effectiveApp != SourceApp.UNKNOWN) effectiveApp else SourceApp.NINETY_NINE_DRIVER
        triggerAutomaticAccessibilityOcrIfNeeded(fallbackApp, targetPkg, now)
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
     * Captura silenciosa via AccessibilityService.takeScreenshot (Android 11 / API 30+)
     * com Watchdog anti-travamento em transições de aplicativos (WhatsApp <-> 99/Uber).
     */
    private fun triggerAutomaticAccessibilityOcrIfNeeded(
        targetApp: SourceApp,
        targetPkg: String,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // Watchdog anti-travamento: se o Android descartou um callback durante a troca de app (ex: abrir WhatsApp),
        // libera o semáforo após 2.4 segundos para nunca parar de ler!
        if (isTakingAutoScreenshot) {
            if (now - lastAutoScreenshotTimeMillis > 2400L) {
                isTakingAutoScreenshot = false
            } else {
                return
            }
        }

        val app = applicationContext as? GoOrNoApplication
        if (app?.overlayPanel?.isCardCurrentlyVisible() == true) return
        if (now - lastAutoScreenshotTimeMillis < 950L) return

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

                        // Corta apenas 4% do topo (barra de relógio/bateria do Android)
                        val cropTop = (softBitmap.height * 0.04).toInt().coerceAtLeast(1)
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
                                        if (txt.isNotEmpty() &&
                                            !txt.contains("GoOrNo", ignoreCase = true) &&
                                            !txt.contains("Aceitar", ignoreCase = true) &&
                                            !txt.contains("Avaliar", ignoreCase = true) &&
                                            !txt.contains("Recusar", ignoreCase = true) &&
                                            !txt.contains("COMPENSA", ignoreCase = true) &&
                                            !txt.contains("AVALIAR", ignoreCase = true) &&
                                            !txt.contains("NÃO COMPENSA", ignoreCase = true) &&
                                            !txt.contains("DADOS INSUFICIENTES", ignoreCase = true) &&
                                            !txt.contains("segure p/ mover", ignoreCase = true) &&
                                            !txt.contains("Toque em qualquer", ignoreCase = true) &&
                                            !txt.startsWith("📍 Origem:") &&
                                            !txt.startsWith("🏁 Destino:") &&
                                            !txt.startsWith("Embarque:") &&
                                            !txt.contains("silencioso está ativado", ignoreCase = true) &&
                                            !txt.contains("retornar chamada", ignoreCase = true)
                                        ) {
                                            val topY = box?.top ?: 0
                                            val centerY = if (box != null) (box.top + box.bottom) / 2 else topY
                                            positionedOcr.add(
                                                PositionedText(
                                                    text = txt,
                                                    topY = topY,
                                                    leftX = box?.left ?: 0,
                                                    centerY = centerY
                                                )
                                            )
                                        }
                                    }
                                }
                                croppedBitmap.recycle()

                                val mergedOcrLines = mergeSameRowNodes(positionedOcr)
                                val normalizedOcr = BrazilianOfferParser.normalizeFragmentedNodes(mergedOcrLines)

                                if (normalizedOcr.isNotEmpty()) {
                                    val inferredApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedOcr)
                                    val effectiveOcrApp = when {
                                        inferredApp != SourceApp.UNKNOWN -> inferredApp
                                        targetApp != SourceApp.UNKNOWN -> targetApp
                                        else -> SourceApp.NINETY_NINE_DRIVER
                                    }

                                    var ocrOffers = extractOffersForApp(
                                        appType = effectiveOcrApp,
                                        lines = normalizedOcr,
                                        method = CaptureMethod.ACCESSIBILITY,
                                        timestampMillis = System.currentTimeMillis()
                                    )

                                    if (ocrOffers.isEmpty()) {
                                        val altApp = if (effectiveOcrApp == SourceApp.NINETY_NINE_DRIVER) {
                                            SourceApp.UBER_DRIVER
                                        } else {
                                            SourceApp.NINETY_NINE_DRIVER
                                        }
                                        ocrOffers = extractOffersForApp(
                                            appType = altApp,
                                            lines = normalizedOcr,
                                            method = CaptureMethod.ACCESSIBILITY,
                                            timestampMillis = System.currentTimeMillis()
                                        )
                                    }

                                    if (ocrOffers.isNotEmpty()) {
                                        consecutiveScansWithoutOffers = 0
                                        RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
                                            AccessibilitySnapshot(
                                                timestampMillis = System.currentTimeMillis(),
                                                packageName = "${effectiveOcrApp.displayName} (Leitura Visual Automática)",
                                                detectedApp = effectiveOcrApp,
                                                nodeCount = normalizedOcr.size,
                                                extractedTexts = normalizedOcr.take(90)
                                            )
                                        )
                                        processAndShowOffers(ocrOffers, normalizedOcr, System.currentTimeMillis())
                                    } else {
                                        consecutiveScansWithoutOffers++
                                        // Se ficou 2 leituras sem nenhuma oferta na tela (ex: abriu WhatsApp ou saiu da chamada),
                                        // limpa o bloqueio de repetição para reabrir imediatamente quando voltar para a oferta!
                                        if (consecutiveScansWithoutOffers >= 2) {
                                            shownOfferTimestamps.clear()
                                            lastScreenContextMode = ""
                                        }
                                    }
                                } else {
                                    consecutiveScansWithoutOffers++
                                    if (consecutiveScansWithoutOffers >= 2) {
                                        shownOfferTimestamps.clear()
                                        lastScreenContextMode = ""
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

    private fun buildStableOfferKey(offer: StandardRideOffer): String {
        val price = offer.offeredPrice ?: 0.0
        val priceRounded = (Math.round(price * 10.0) / 10.0)
        val totalKm = offer.totalDistanceKmExplicit
            ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
        val kmRounded = (Math.round(totalKm * 2.0) / 2.0)
        val origPrefix = offer.originAddress?.take(8)?.lowercase(Locale.getDefault()) ?: ""
        val destPrefix = offer.destinationAddress?.take(8)?.lowercase(Locale.getDefault()) ?: ""
        return "${offer.sourceApp.name}|$priceRounded|$kmRounded|$origPrefix|$destPrefix"
    }

    private fun processAndShowOffers(
        parsedOffers: List<StandardRideOffer>,
        screenLines: List<String>,
        now: Long
    ) {
        if (parsedOffers.isEmpty()) return

        val app = applicationContext as? GoOrNoApplication ?: return

        // Se o card já está atualmente aberto na tela do motorista, não reabre nem reinicia
        if (app.overlayPanel.isCardCurrentlyVisible()) {
            return
        }

        // Remove apenas chaves que já expiraram há mais de 90 segundos
        val expiredKeys = shownOfferTimestamps.entries
            .filter { now - it.value > 90_000L }
            .map { it.key }
        expiredKeys.forEach { shownOfferTimestamps.remove(it) }

        // Filtra estritamente ofertas que ainda NÃO foram exibidas nos últimos 90 segundos
        val unshownOffers = parsedOffers.filter { offer ->
            val key = buildStableOfferKey(offer)
            val lastShown = shownOfferTimestamps[key]
            lastShown == null || (now - lastShown > 90_000L)
        }

        if (unshownOffers.isEmpty()) {
            // Todos os chamados já foram avaliados e exibidos recentemente; não repete para não incomodar o motorista!
            return
        }

        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluations = unshownOffers
                .map { offer -> FinancialEngine.evaluate(offer, config) }
                .filter { it.verdict != com.creativeam.goorno.model.TrafficLightVerdict.GRAY_NEUTRAL }

            // Nunca exibe o card flutuante com "DADOS INSUFICIENTES" no monitoramento automático,
            // evitando cobrir o preço da tela ou travar a leitura do Radar de Viagens da Uber!
            if (evaluations.isEmpty()) {
                return@launch
            }

            for (eval in evaluations) {
                val key = buildStableOfferKey(eval.offer)
                shownOfferTimestamps[key] = now
            }

            RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

            // Escolhe a avaliação mais completa (com Origem, Destino e Tempo) para persistir na Notificação do Topo!
            val richestEval = evaluations.maxByOrNull { eval ->
                (if (!eval.offer.originAddress.isNullOrBlank()) 3 else 0) +
                    (if (!eval.offer.destinationAddress.isNullOrBlank()) 3 else 0) +
                    (if (eval.totalConsideredDurationMin != null) 2 else 0) +
                    (if (eval.totalConsideredDistanceKm != null) 1 else 0)
            } ?: evaluations.first()

            RuntimeDiagnosticsBus.publishEvaluation(richestEval)
            app.overlayPanel.applyConfig(config)
            app.overlayPanel.showEvaluations(evaluations)
            CopilotNotificationReceiver.showActiveNotification(
                context = this@GoOrNoAccessibilityService,
                latestEvaluation = richestEval,
                showProfit = config.showProfitOnOverlay,
                showCost = config.showCostOnOverlay
            )

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

    private fun mergeSameRowNodes(nodes: List<PositionedText>): List<String> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedWith(compareBy<PositionedText>({ it.centerY }, { it.leftX }))

        val rows = mutableListOf<MutableList<PositionedText>>()
        for (item in sorted) {
            val lastRow = rows.lastOrNull()
            if (lastRow != null && kotlin.math.abs(lastRow.first().centerY - item.centerY) <= 28) {
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
        depth: Int,
        requireVisible: Boolean = true
    ) {
        if (node == null || depth > 50 || output.size >= 600) return
        if (requireVisible && !node.isVisibleToUser) return

        val rect = Rect()
        try {
            node.getBoundsInScreen(rect)
        } catch (_: Exception) {
        }

        if (requireVisible && (rect.width() <= 0 || rect.height() <= 0)) {
            return
        }

        val centerY = if (rect.height() > 0) (rect.top + rect.bottom) / 2 else rect.top

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
            output.add(PositionedText(txt, rect.top, rect.left, centerY))
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            if (output.none { it.text == desc && kotlin.math.abs(it.centerY - centerY) <= 14 }) {
                output.add(PositionedText(desc, rect.top, rect.left, centerY))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                if (output.none { it.text == hint }) {
                    output.add(PositionedText(hint, rect.top, rect.left, centerY))
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
                collectPositionedTexts(child, output, depth + 1, requireVisible)
            }
        }
    }

    override fun onInterrupt() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.hidePanel()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.hidePanel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
