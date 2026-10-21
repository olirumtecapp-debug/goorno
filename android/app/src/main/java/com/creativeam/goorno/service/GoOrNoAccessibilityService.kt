package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.creativeam.goorno.model.OfferLifecycleState
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
    private var currentLifecycleState: OfferLifecycleState = OfferLifecycleState.OUTSIDE_ALLOWED_APP
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
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = info
        } catch (_: Throwable) {
        }

        RuntimeDiagnosticsBus.setAccessibilityConnected(true)
        try {
            GoOrNoForegroundKeepAliveService.start(this)
        } catch (_: Throwable) {}
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            shownOfferTimestamps.clear()
            isTakingAutoScreenshot = false
            System.gc()
        }
    }

    
    private fun isHeavyMediaOrSocialApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("instagram") ||
            p.contains("spotify") ||
            p.contains("youtube") ||
            p.contains("netflix") ||
            p.contains("tiktok") ||
            p.contains("facebook") ||
            p.contains("zhiliaoapp") ||
            p.contains("musically")
    }

    private fun startActivePollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val app = applicationContext as? GoOrNoApplication
                    val isHeavyAppInFront = isHeavyMediaOrSocialApp(lastKnownForegroundPkg)
                    if (isHeavyAppInFront) {
                        // Motorista está no Spotify ou Instagram: suspende escaneamento ativo para poupar RAM e CPU
                        System.gc()
                        delay(2000L)
                        continue
                    }

                    if (app?.overlayPanel?.isCopilotMonitoringEnabled() == true &&
                        app.overlayPanel.isCardCurrentlyVisible() == false
                    ) {
                        scanCurrentScreen(eventPkg = null, forceThrottleBypass = true)
                    }
                } catch (_: Throwable) {
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
        // Se o evento for do próprio GoOrNo (timer, arrasto, UI), ignora
        if (evtPkg == packageName || evtPkg.contains("com.creativeam.goorno")) {
            return
        }

        // Se o evento for de teclado ou painel de notificações, não processa
        if (evtPkg == "com.android.systemui" ||
            evtPkg.contains("inputmethod", ignoreCase = true) ||
            evtPkg.contains("keyboard", ignoreCase = true)
        ) {
            return
        }

        // TRAVA RIGOROSA DE PACOTE:
        // Se o evento não for de um app de motorista permitido (Uber Driver ou 99 Motorista):
        val isEventDriverPkg = SourceApp.isAllowedDriverPackage(evtPkg)
        val activeRootPkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        val isActiveDriverPkg = SourceApp.isAllowedDriverPackage(activeRootPkg)

        if (!isEventDriverPkg && !isActiveDriverPkg) {
            // O usuário está em outro app (WhatsApp, Navegador, etc.) ou na tela inicial:
            if (currentLifecycleState != OfferLifecycleState.OUTSIDE_ALLOWED_APP) {
                currentLifecycleState = OfferLifecycleState.OUTSIDE_ALLOWED_APP
                isTakingAutoScreenshot = false
                shownOfferTimestamps.clear()
                lastScreenContextMode = ""
                consecutiveScansWithoutOffers = 0
                app?.overlayPanel?.clearRecentlyDismissed()
                app?.overlayPanel?.hideOfferCardOnly()
            }
            lastKnownForegroundPkg = evtPkg
            return
        }

        if (evtPkg.isNotBlank() && evtPkg != lastKnownForegroundPkg) {
            shownOfferTimestamps.clear()
            lastScreenContextMode = ""
            consecutiveScansWithoutOffers = 0
            app?.overlayPanel?.clearRecentlyDismissed()
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

        // 1. Identifica estritamente a janela ativa em primeiro plano
        val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        val activePkg = activeRoot?.packageName?.toString()
            ?: eventPkg?.takeIf { SourceApp.isAllowedDriverPackage(it) }
            ?: lastKnownForegroundPkg.takeIf { SourceApp.isAllowedDriverPackage(it) }
            ?: ""

        // Se a tela ativa não pertence a um aplicativo de motorista permitido, aborta imediatamente
        if (!SourceApp.isAllowedDriverPackage(activePkg)) {
            currentLifecycleState = OfferLifecycleState.OUTSIDE_ALLOWED_APP
            isTakingAutoScreenshot = false
            return
        }

        val targetPkg = activePkg
        val targetApp = SourceApp.fromPackageName(targetPkg)
        if (targetApp == SourceApp.UNKNOWN || targetApp == SourceApp.SIMULATION) {
            currentLifecycleState = OfferLifecycleState.OUTSIDE_ALLOWED_APP
            isTakingAutoScreenshot = false
            return
        }

        // 2. Coleta nós ESTRITAMENTE da janela do pacote alvo (nunca de outras janelas)
        val positionedNodes = mutableListOf<PositionedText>()
        if (activeRoot != null && activeRoot.packageName?.toString() == targetPkg) {
            collectPositionedTexts(activeRoot, positionedNodes, depth = 0, requireVisible = true)
            if (positionedNodes.size < 3) {
                positionedNodes.clear()
                collectPositionedTexts(activeRoot, positionedNodes, depth = 0, requireVisible = false)
            }
        } else {
            try {
                val allWindows = windows
                if (!allWindows.isNullOrEmpty()) {
                    for (win in allWindows) {
                        val root = win.root ?: continue
                        if (root.packageName?.toString() == targetPkg) {
                            collectPositionedTexts(root, positionedNodes, depth = 0, requireVisible = true)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (positionedNodes.isEmpty()) {
            currentLifecycleState = OfferLifecycleState.ALLOWED_NO_OFFER
            // Fallback OCR automático apenas se o pacote em primeiro plano for comprovadamente o motorista
            triggerAutomaticAccessibilityOcrIfNeeded(targetApp, targetPkg, now)
            return
        }

        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)

        // 3. DETECÇÃO DE OCIOSIDADE E FECHAMENTO DE CARDS RESIDUAIS (ALLOWED_NO_OFFER):
        val joinedScreenLower = normalizedTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasRideActionBtn = joinedScreenLower.contains("escolher") ||
            joinedScreenLower.contains("aceitar") ||
            joinedScreenLower.contains("selecionar") ||
            joinedScreenLower.contains("deslize") ||
            joinedScreenLower.contains("nova corrida")

        val isIdleSearchingScreen = (joinedScreenLower.contains("buscando solicitações") ||
            joinedScreenLower.contains("buscando solicitacoes") ||
            joinedScreenLower.contains("ainda buscando") ||
            joinedScreenLower.contains("mapa de chamadas") ||
            joinedScreenLower.contains("aguarde ou veja o mapa") ||
            joinedScreenLower.contains("procurando viagens") ||
            joinedScreenLower.contains("você está online") ||
            joinedScreenLower.contains("voce esta online")) && !hasRideActionBtn

        if (isIdleSearchingScreen || (!hasRideActionBtn && normalizedTexts.none { it.contains("R$") })) {
            currentLifecycleState = OfferLifecycleState.ALLOWED_NO_OFFER
            (applicationContext as? GoOrNoApplication)?.overlayPanel?.hideOfferCardOnly()
            shownOfferTimestamps.clear()
            lastScreenContextMode = ""
            consecutiveScansWithoutOffers++
            return
        }

        if (normalizedTexts.isNotEmpty()) {
            RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
                AccessibilitySnapshot(
                    timestampMillis = now,
                    packageName = targetPkg,
                    detectedApp = targetApp,
                    nodeCount = normalizedTexts.size,
                    extractedTexts = normalizedTexts.take(90)
                )
            )
        }

        // 4. EXTRAÇÃO EXCLUSIVA PARA O APLICATIVO ALVO (OFFER_CANDIDATE):
        currentLifecycleState = OfferLifecycleState.OFFER_CANDIDATE
        val parsedOffers = extractOffersForApp(targetApp, normalizedTexts, CaptureMethod.ACCESSIBILITY, now)
        if (parsedOffers.isEmpty()) {
            if (hasRideActionBtn) {
                triggerAutomaticAccessibilityOcrIfNeeded(targetApp, targetPkg, now)
            }
            return
        }

        // 5. VALIDAÇÃO RIGOROSA E ESTABILIZAÇÃO (VALIDATING_OFFER -> OFFER_ACTIVE):
        currentLifecycleState = OfferLifecycleState.VALIDATING_OFFER
        val completeAccessibilityOffers = parsedOffers.filter { offer ->
            val price = offer.offeredPrice ?: 0.0
            val km = offer.totalDistanceKmExplicit ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
            val min = offer.totalDurationMinExplicit ?: ((offer.pickupDurationMin ?: 0) + (offer.tripDurationMin ?: 0))
            val hasAddresses = !offer.originAddress.isNullOrBlank() || !offer.destinationAddress.isNullOrBlank()

            val isSanePrice = price in 4.50..350.0 || (km >= 60.0 && price <= 1500.0)
            val isSaneDuration = min in 1..240
            val isSaneKm = km in 0.2..300.0

            isSanePrice && isSaneKm && isSaneDuration && hasAddresses && !offer.hasFieldConflict
        }

        if (completeAccessibilityOffers.isNotEmpty()) {
            currentLifecycleState = OfferLifecycleState.OFFER_ACTIVE
            consecutiveScansWithoutOffers = 0
            processAndShowOffers(completeAccessibilityOffers, normalizedTexts, now)
            return
        }

        // Se a árvore de acessibilidade contém oferta mas ocultou valores ou distâncias (ex: canvas 99):
        triggerAutomaticAccessibilityOcrIfNeeded(targetApp, targetPkg, now)
    }

    private fun isSocialMediaApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("instagram") ||
            p.contains("zhiliaoapp") ||
            p.contains("musically") ||
            p.contains("tiktok") ||
            p.contains("netflix") ||
            p.contains("youtube")
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
        if (targetPkg == packageName || targetPkg.contains("com.creativeam.goorno")) {
            isTakingAutoScreenshot = false
            return
        }

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
                        } catch (_: Throwable) {
                            null
                        }

                        if (hwBitmap == null) {
                            try {
                                hwBuffer.close()
                            } catch (_: Throwable) {
                            }
                            isTakingAutoScreenshot = false
                            return
                        }

                        val softBitmap = try {
                            hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        }
                        try {
                            hwBitmap.recycle()
                        } catch (_: Throwable) {}
                        try {
                            hwBuffer.close()
                        } catch (_: Throwable) {
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
                        } catch (_: Throwable) {
                            softBitmap
                        }
                        if (croppedBitmap != softBitmap) {
                            try { softBitmap.recycle() } catch (_: Throwable) {}
                        }

                        // Otimização para aparelhos de 4GB (como Motorola moto g05):
                        // Se a largura for superior a 720p, reduz pela metade para economizar 75% da RAM sem perder qualidade no OCR!
                        val finalOcrBitmap = try {
                            if (croppedBitmap.width > 720) {
                                val halfW = croppedBitmap.width / 2
                                val halfH = croppedBitmap.height / 2
                                val scaled = Bitmap.createScaledBitmap(croppedBitmap, halfW, halfH, true)
                                if (scaled != croppedBitmap) {
                                    try { croppedBitmap.recycle() } catch (_: Throwable) {}
                                }
                                scaled
                            } else {
                                croppedBitmap
                            }
                        } catch (_: Throwable) {
                            croppedBitmap
                        }

                        val inputImage = try {
                            InputImage.fromBitmap(finalOcrBitmap, 0)
                        } catch (_: Throwable) {
                            try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                            isTakingAutoScreenshot = false
                            return
                        }

                        mlKitRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val fullOcrText = visionText.text
                                val hasGoOrNoScreenSignature = fullOcrText.contains("Início Ganhos Janela Histórico", ignoreCase = true) ||
                                    (fullOcrText.contains("Histórico de Corridas", ignoreCase = true) && fullOcrText.contains("GoOrNo", ignoreCase = true)) ||
                                    (fullOcrText.contains("Auditoria de chamadas", ignoreCase = true) && fullOcrText.contains("Total de ofertas", ignoreCase = true))
                                if (hasGoOrNoScreenSignature) {
                                    try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                                    isTakingAutoScreenshot = false
                                    return@addOnSuccessListener
                                }
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
                                try { finalOcrBitmap.recycle() } catch (_: Throwable) {}

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
                                try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                                isTakingAutoScreenshot = false
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        isTakingAutoScreenshot = false
                    }
                }
            )
        } catch (_: Throwable) {
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

            // Grava apenas ofertas autênticas, completas e válidas no banco local Room (blindagem anti-sujeira)
            if (config.saveHistoryEnabled) {
                for (evaluation in evaluations) {
                    val offer = evaluation.offer
                    val isSanePrice = (offer.offeredPrice ?: 0.0) >= 4.50
                    val totalKm = evaluation.totalConsideredDistanceKm ?: 0.0
                    val totalMin = evaluation.totalConsideredDurationMin ?: 0
                    val hasAddresses = !offer.originAddress.isNullOrBlank() || !offer.destinationAddress.isNullOrBlank()
                    val isLegitOffer = isSanePrice && totalKm >= 0.2 && totalMin > 0 && hasAddresses && !offer.hasFieldConflict

                    if (!offer.isSimulated && isLegitOffer) {
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
        try {
            if (requireVisible && !node.isVisibleToUser) return

            val rect = Rect()
            try {
                node.getBoundsInScreen(rect)
            } catch (_: Throwable) {
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
                } catch (_: Throwable) {
                    null
                }
                if (child != null) {
                    collectPositionedTexts(child, output, depth + 1, requireVisible)
                }
            }
        } catch (_: Throwable) {
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
