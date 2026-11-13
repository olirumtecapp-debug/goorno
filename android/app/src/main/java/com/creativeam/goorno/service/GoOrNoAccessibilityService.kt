package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.diagnostics.AccessibilitySnapshot
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.detector.RideScreenContext
import com.creativeam.goorno.detector.RideScreenContextClassifier
import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.OfferLifecycleState
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.ninetynine.NinetyNineVisualRatingMatcher
import com.creativeam.goorno.reader.ninetynine.NinetyNineListDisplayPolicy
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

    

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val shownOfferTimestamps = LinkedHashMap<String, Long>()
    private var currentLifecycleState: OfferLifecycleState = OfferLifecycleState.OUTSIDE_ALLOWED_APP
    private var lastScreenContextMode: String = ""
    private var lastKnownForegroundPkg: String = ""
    private var lastScanTimeMillis: Long = 0L
    private val isScanInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastAutoScreenshotTimeMillis: Long = 0L
    private var lastCardClosedTimeMillis: Long = 0L
    private var lastOfferProcessedTimeMillis: Long = 0L
    private var lastRatingOcrAttemptMillis: Long = 0L
    private var lastRatingOcrOfferKey: String = ""
    private var last99ListSignature: String = ""
    private var wasViewing99List: Boolean = false
    @Volatile private var recentlyDisplayedOffers: List<StandardRideOffer> = emptyList()
    @Volatile private var recentlyDisplayedAtMillis: Long = 0L
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
        // KeepAlive desnecessario no servico de acessibilidade nativo
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
                    val currentRootPkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Throwable) { null }
                    if (currentRootPkg != null && SourceApp.isAllowedDriverPackage(currentRootPkg)) {
                        lastKnownForegroundPkg = currentRootPkg
                    }

                    val isDriverAppActive = SourceApp.isAllowedDriverPackage(lastKnownForegroundPkg)
                    val loopDelay = if (isDriverAppActive) 150L else 350L
                    val minScanInterval = if (isDriverAppActive) 140L else 320L

                    if (app?.overlayPanel?.isCopilotMonitoringEnabled() == true) {
                        val now = System.currentTimeMillis()
                        if (app.overlayPanel.isCardCurrentlyVisible()) {
                            checkIfOfferDismissedAndCloseCard(activeRootPkg = null, eventPkg = null)
                        }
                        if (now - lastScanTimeMillis >= minScanInterval) {
                            scanCurrentScreen(eventPkg = null, forceThrottleBypass = false)
                        }
                    }
                } catch (_: Throwable) {
                }
                val isDriverAppActive = SourceApp.isAllowedDriverPackage(lastKnownForegroundPkg)
                val loopDelay = if (isDriverAppActive) 150L else 400L
                delay(loopDelay)
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
        val isEventDriverPkg = SourceApp.isAllowedDriverPackage(evtPkg)
        val activeRootPkg = if (isEventDriverPkg) evtPkg else try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        val isActiveDriverPkg = isEventDriverPkg || SourceApp.isAllowedDriverPackage(activeRootPkg)

        if (!isActiveDriverPkg) {
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

        val effectivePkg = if (isEventDriverPkg) evtPkg else (activeRootPkg ?: evtPkg)
        val isAppTransitionToDriver = (effectivePkg.isNotBlank() && effectivePkg != lastKnownForegroundPkg)
        if (effectivePkg.isNotBlank() && effectivePkg != lastKnownForegroundPkg) {
            shownOfferTimestamps.clear()
            lastScreenContextMode = ""
            consecutiveScansWithoutOffers = 0
            app?.overlayPanel?.clearRecentlyDismissed()
            app?.overlayPanel?.hideOfferCardOnly()
            lastKnownForegroundPkg = effectivePkg
        }

        if (app?.overlayPanel?.isCardCurrentlyVisible() == true) {
            checkIfOfferDismissedAndCloseCard(activeRootPkg = activeRootPkg, eventPkg = evtPkg)
        }

        val eventTexts = event?.text?.joinToString(" ") ?: ""
        val eventContentDesc = event?.contentDescription?.toString() ?: ""
        val eventCombined = "$eventTexts $eventContentDesc"
        val isLikelyOfferEvent = isAppTransitionToDriver ||
            eventCombined.contains("R$") ||
            eventCombined.contains("Pop", ignoreCase = true) ||
            eventCombined.contains("Aceitar", ignoreCase = true) ||
            eventCombined.contains("Escolher", ignoreCase = true) ||
            eventCombined.contains("Uber", ignoreCase = true) ||
            eventCombined.contains("min", ignoreCase = true)

        scanCurrentScreen(eventPkg = effectivePkg, forceThrottleBypass = isLikelyOfferEvent)
    }

    private fun scanCurrentScreen(eventPkg: String?, forceThrottleBypass: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceThrottleBypass && now - lastScanTimeMillis < 100L) return
        if (!forceThrottleBypass && now - lastCardClosedTimeMillis < 120L) return
        if (!isScanInProgress.compareAndSet(false, true)) {
            return
        }
        try {
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

        // 2. Coleta nós da janela ativa em primeiro plano (sem sobrecarga de IPC em janelas secundárias)
        val positionedNodes = mutableListOf<PositionedText>()
        if (activeRoot != null && activeRoot.packageName?.toString() == targetPkg) {
            collectPositionedTexts(activeRoot, positionedNodes, depth = 0, requireVisible = false)
        }

        if (positionedNodes.isEmpty()) {
            currentLifecycleState = OfferLifecycleState.ALLOWED_NO_OFFER
            // Fallback visual silencioso se a árvore de acessibilidade estiver vazia ou oculta
            triggerAutomaticAccessibilityOcrIfNeeded(targetApp, targetPkg, now)
            return
        }

        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)
        // Mudança de página: a mesma corrida pode aparecer primeiro no mapa e
        // depois na lista. Não mantenha o filtro de duplicidade entre telas.
        if (targetApp == SourceApp.NINETY_NINE_DRIVER &&
            normalizedTexts.none { it.contains("Solicitações", ignoreCase = true) ||
                it.contains("Solicitacoes", ignoreCase = true) }
        ) {
            wasViewing99List = false
        }

        // 2. CLASSIFICADOR CENTRAL DE CONTEXTO DE TELA (Uber e 99):
        val screenContext = RideScreenContextClassifier.classify(targetApp, normalizedTexts)
        if (!screenContext.shouldAnalyze) {
            currentLifecycleState = OfferLifecycleState.ALLOWED_NO_OFFER
            (applicationContext as? GoOrNoApplication)?.overlayPanel?.hideOfferCardOnly()
            shownOfferTimestamps.clear()
            lastScreenContextMode = ""
            consecutiveScansWithoutOffers++
            return
        }

        // 3. DETECÇÃO DE OCIOSIDADE E FECHAMENTO DE CARDS RESIDUAIS (ALLOWED_NO_OFFER):
        val joinedScreenLower = normalizedTexts.joinToString(" ").lowercase(Locale.ROOT)
        // hasRideActionBtn — apenas tokens EXCLUSIVOS de telas de oferta real.
        val hasRideActionBtn = joinedScreenLower.contains("aceitar") ||
            joinedScreenLower.contains("escolher") ||
            joinedScreenLower.contains("selecionar") ||
            joinedScreenLower.contains("deslize") ||
            joinedScreenLower.contains("nova corrida") ||
            joinedScreenLower.contains("uberx") ||
            joinedScreenLower.contains("comfort") ||
            joinedScreenLower.contains("black") ||
            joinedScreenLower.contains("flash") ||
            joinedScreenLower.contains("priority") ||
            joinedScreenLower.contains("prioridade") ||
            joinedScreenLower.contains("exclusivo") ||
            joinedScreenLower.contains("radar de viagens") ||
            joinedScreenLower.contains("nova viagem") ||
            joinedScreenLower.contains("entregar") ||
            joinedScreenLower.contains("pgto. no app") ||
            joinedScreenLower.contains("pagamento no app") ||
            joinedScreenLower.contains("embarque") ||
            joinedScreenLower.contains("pop") ||
            joinedScreenLower.contains("99pop") ||
            joinedScreenLower.contains("99moto") ||
            joinedScreenLower.contains("99negocia") ||
            joinedScreenLower.contains("99plus")

        // ============================================================
        // OFFER GUARD v3 — BLINDAGEM TOTAL DE TELA OCIOSA:
        // Se a tela tem dados financeiros autênticos (Preço R$ E distância/tempo),
        // NÃO é tela ociosa, mesmo se o mapa ao fundo estiver "Buscando".
        // ============================================================
        val hasCurrency = normalizedTexts.any { it.contains("R$") }
        val hasDistanceOrTime = normalizedTexts.any {
            it.contains("min", ignoreCase = true) || it.contains("km", ignoreCase = true) || it.contains(" m", ignoreCase = true)
        }
        val hasRealOfferData = hasCurrency && hasDistanceOrTime

        val isPainelOrIdleScreen = !hasRealOfferData && (
            // Modo "Buscando" (motorista online esperando corrida no mapa da 99)
            (joinedScreenLower.contains("buscando") && !hasRideActionBtn) ||
            // Modo "Conectar" (motorista offline ou painel inicial)
            (joinedScreenLower.contains("conectar") && !hasRideActionBtn) ||
            // Anúncios de combustível 99 Abastece sem chamada real
            (joinedScreenLower.contains("abastece") && !hasRideActionBtn) ||
            // Contador da gaveta de solicitações (ex: "Solicitações (3)") sem chamada ativa
            ((joinedScreenLower.contains("solicitações (") || joinedScreenLower.contains("solicitacoes (")) && !hasRideActionBtn) ||
            // Recompensas / Ganhos
            ((joinedScreenLower.contains("recompensas") || joinedScreenLower.contains("central de ganhos")) && !hasRideActionBtn)
        )

        if (isPainelOrIdleScreen) {
            currentLifecycleState = OfferLifecycleState.ALLOWED_NO_OFFER
            (applicationContext as? GoOrNoApplication)?.overlayPanel?.hideOfferCardOnly()
            shownOfferTimestamps.clear()
            lastScreenContextMode = ""
            consecutiveScansWithoutOffers++
            return
        }

        val isIdleSearchingScreen = !hasCurrency && !hasRideActionBtn && (
            joinedScreenLower.contains("buscando solicitações") ||
            joinedScreenLower.contains("buscando solicitacoes") ||
            joinedScreenLower.contains("ainda buscando") ||
            joinedScreenLower.contains("mapa de chamadas") ||
            joinedScreenLower.contains("aguarde ou veja o mapa") ||
            joinedScreenLower.contains("procurando viagens") ||
            joinedScreenLower.contains("você está online") ||
            joinedScreenLower.contains("voce esta online")
        )

        if (isIdleSearchingScreen || (!hasRideActionBtn && !hasCurrency)) {
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

            // Se possui Preço, Km e Tempo consistentes, exibe a avaliação financeira de primeira (sem travar se os endereços ainda estiverem renderizando)
            isSanePrice && isSaneKm && isSaneDuration && !offer.hasFieldConflict
        }

        if (completeAccessibilityOffers.isNotEmpty()) {
            // Captura complementar da nota somente DEPOIS de exibir uma nova oferta.
            // Nunca inicia OCR enquanto a leitura principal ainda percorre a árvore.
            currentLifecycleState = OfferLifecycleState.OFFER_ACTIVE
            consecutiveScansWithoutOffers = 0
            processAndShowOffers(completeAccessibilityOffers, normalizedTexts, now)
            return
        }

        // Se a árvore de acessibilidade contém oferta mas ocultou valores ou distâncias (ex: canvas 99):
        triggerAutomaticAccessibilityOcrIfNeeded(targetApp, targetPkg, now)
        } finally {
            isScanInProgress.set(false)
        }
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
        now: Long,
        ratingOnly: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // Classifica o contexto da tela antes de tirar screenshot/OCR
        val activeRootFast = try { rootInActiveWindow } catch (_: Exception) { null }
        if (activeRootFast != null) {
            val fastTexts = mutableListOf<String>()
            collectFastTexts(activeRootFast, fastTexts, maxNodes = 30)
            val fastContext = RideScreenContextClassifier.classify(targetApp, fastTexts)
            if (fastContext == RideScreenContext.NINETYNINE_REQUEST_LIST || fastContext == RideScreenContext.UBER_RADAR_LIST) {
                isTakingAutoScreenshot = false
                return
            }
        }
        val app = applicationContext as? GoOrNoApplication
        if (!ratingOnly &&
            (app?.overlayPanel?.isCardCurrentlyVisible() == true || now - lastOfferProcessedTimeMillis < 2_500L)) {
            isTakingAutoScreenshot = false
            return
        }
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

        if (!ratingOnly && now - lastAutoScreenshotTimeMillis < 950L) return

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
                                // Valida atualidade da captura: se o motorista mudou de app ou a tela mudou antes do OCR terminar, descarta
                                val currentForeground = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
                                    ?: lastKnownForegroundPkg
                                val ownOverlayFocused = ratingOnly &&
                                    currentForeground == packageName &&
                                    lastKnownForegroundPkg == targetPkg &&
                                    (applicationContext as? GoOrNoApplication)?.overlayPanel?.isCardCurrentlyVisible() == true
                                if (currentForeground != targetPkg && !ownOverlayFocused) {
                                    try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                                    isTakingAutoScreenshot = false
                                    return@addOnSuccessListener
                                }
                                if (System.currentTimeMillis() - now > 2200L) {
                                    try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                                    isTakingAutoScreenshot = false
                                    return@addOnSuccessListener
                                }
                                if (!ratingOnly && now < lastOfferProcessedTimeMillis) {
                                    try { finalOcrBitmap.recycle() } catch (_: Throwable) {}
                                    isTakingAutoScreenshot = false
                                    return@addOnSuccessListener
                                }

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
                                        method = CaptureMethod.OCR,
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

                                    // FILTRO RIGOROSO DE SANIDADE NO OCR (elimina glitches como 1009 min ou preços inexistentes)
                                    val validOcrOffers = ocrOffers.filter { offer ->
                                        val price = offer.offeredPrice ?: 0.0
                                        val km = offer.totalDistanceKmExplicit ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
                                        val min = offer.totalDurationMinExplicit ?: ((offer.pickupDurationMin ?: 0) + (offer.tripDurationMin ?: 0))
                                        val isSanePrice = price in 4.50..350.0 || (km >= 60.0 && price <= 1500.0)
                                        val isSaneDuration = min in 1..240 // NUNCA aceita tempos absurdos como 1009 min!
                                        val isSaneKm = km in 0.2..300.0
                                        val ocrContext = RideScreenContextClassifier.classify(effectiveOcrApp, normalizedOcr)
                                        val isAllowedContext = ocrContext != RideScreenContext.NINETYNINE_REQUEST_LIST && ocrContext != RideScreenContext.UBER_RADAR_LIST

                                        isSanePrice && isSaneKm && isSaneDuration && !offer.hasFieldConflict && isAllowedContext
                                    }

                                    if (ratingOnly) {
                                        // OCR parcial frequentemente não enxerga preço sob o overlay,
                                        // mas enxerga a linha "4,94 • 728 corridas" mais abaixo.
                                        // O fallback seguro exige uma única oferta e uma única nota.
                                        val ratingGeometry = positionedOcr.map {
                                            NinetyNineVisualRatingMatcher.VisualText(it.text, it.centerY)
                                        }
                                        mainHandler.post {
                                            updateDisplayedPassengerRatings(
                                                validOcrOffers, normalizedOcr, ratingGeometry, now
                                            )
                                        }
                                    } else if (validOcrOffers.isNotEmpty()) {
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
                                        mainHandler.post { processAndShowOffers(validOcrOffers, normalizedOcr, System.currentTimeMillis()) }
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

    private fun updateDisplayedPassengerRatings(
        ocrOffers: List<StandardRideOffer>,
        ocrLines: List<String>,
        ratingGeometry: List<NinetyNineVisualRatingMatcher.VisualText>,
        capturedAt: Long
    ) {
        val app = applicationContext as? GoOrNoApplication ?: return
        if (!app.overlayPanel.isCardCurrentlyVisible()) return
        if (recentlyDisplayedAtMillis < capturedAt - 2_500L ||
            System.currentTimeMillis() - recentlyDisplayedAtMillis > 5_000L) return
        val original = recentlyDisplayedOffers
        if (original.isEmpty()) return
        // OCR com posição Y consegue associar a linha da nota ao preço correto
        // mesmo com dois cartões na mesma tela. Mantém as outras métricas intactas.
        val positionMatched = NinetyNineVisualRatingMatcher.enrich(original, ratingGeometry)
        val matching = NinetyNineOfferReader.enrichRatingsFromOcr(positionMatched, ocrOffers)
        val enhanced = if (matching != original) matching else
            NinetyNineOfferReader.recoverSingleOfferRatingFromOcr(original, ocrLines)
        if (enhanced == original) return
        serviceScope.launch {
            val config = app.preferencesRepository.getInMemoryConfig()
            val evaluations = enhanced.map { FinancialEngine.evaluate(it, config) }
                .filter { it.verdict != com.creativeam.goorno.model.TrafficLightVerdict.GRAY_NEUTRAL }
            if (evaluations.isNotEmpty()) {
                mainHandler.post {
                    // Atualiza o card sem gerar nova oferta, novo registro ou novo alerta.
                    if (app.overlayPanel.isCardCurrentlyVisible() &&
                        recentlyDisplayedOffers == original) {
                        recentlyDisplayedOffers = enhanced
                        app.overlayPanel.updateRatingsWithoutReset(evaluations)
                    }
                }
            }
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
        // Classifica o contexto da tela antes de abrir o card flutuante
        val firstApp = parsedOffers.firstOrNull()?.sourceApp ?: SourceApp.NINETY_NINE_DRIVER
        val dispatchContext = RideScreenContextClassifier.classify(firstApp, screenLines)
        if (parsedOffers.isEmpty() && (dispatchContext == RideScreenContext.NINETYNINE_REQUEST_LIST || dispatchContext == RideScreenContext.UBER_RADAR_LIST || dispatchContext == RideScreenContext.NO_OFFER)) {
            app.overlayPanel.hideOfferCardOnly()
            wasViewing99List = false
            last99ListSignature = ""
            return
        }
        wasViewing99List = false
        last99ListSignature = ""

        // Remove chaves que expiraram há mais de 120 segundos
        val expiredKeys = shownOfferTimestamps.entries
            .filter { now - it.value > 120_000L }
            .map { it.key }
        expiredKeys.forEach { shownOfferTimestamps.remove(it) }

        // Identifica ofertas novas e ofertas repetidas (re-oferta após término ou recusa)
        val validOffers = mutableListOf<StandardRideOffer>()
        val alreadyShownBeforeList = mutableSetOf<String>()
        for (offer in parsedOffers) {
            val key = buildStableOfferKey(offer)
            val lastShown = shownOfferTimestamps[key]
            if (lastShown != null) {
                alreadyShownBeforeList.add(key)
            }
            if (lastShown == null) {
                // Primeira vez que esta oferta toca
                validOffers.add(offer.copy(isRepeatedOffer = false))
            } else if (now - lastShown >= 15_000L && now - lastShown <= 120_000L) {
                // Já tocou há mais de 15 segundos (nova chamada/re-oferta do app)
                validOffers.add(offer.copy(isRepeatedOffer = true))
            } else {
                // Ainda está dentro dos 15 segundos do toque original; ignora para não repicar o card
            }
        }

        // 1. Enriquecimento Instantâneo de Nota (0ms):
        // Se o card já está visível para a MESMA corrida mas estava com nota nula/tracinho,
        // e o scan atual da acessibilidade encontrou a nota do passageiro (ex: animação do mapinha terminou),
        // atualiza o card imediatamente sem recriar, sem flicker e sem depender de OCR!
        if (app.overlayPanel.isCardCurrentlyVisible() && recentlyDisplayedOffers.isNotEmpty()) {
            val currentOffers = recentlyDisplayedOffers
            var enrichedAny = false
            val enrichedOffers = currentOffers.map { current ->
                if (current.passengerRating == null && !current.passengerIsNew) {
                    val matching = parsedOffers.firstOrNull { candidate ->
                        candidate.sourceApp == current.sourceApp &&
                        candidate.passengerRating != null &&
                        Math.abs((candidate.offeredPrice ?: 0.0) - (current.offeredPrice ?: 0.0)) < 0.20
                    }
                    if (matching != null && matching.passengerRating != null) {
                        enrichedAny = true
                        current.copy(
                            passengerRating = matching.passengerRating,
                            passengerIsNew = matching.passengerIsNew
                        )
                    } else {
                        current
                    }
                } else {
                    current
                }
            }

            if (enrichedAny) {
                recentlyDisplayedOffers = enrichedOffers
                serviceScope.launch {
                    val config = app.preferencesRepository.getInMemoryConfig()
                    val evaluations = enrichedOffers.map { FinancialEngine.evaluate(it, config) }
                        .filter { it.verdict != com.creativeam.goorno.model.TrafficLightVerdict.GRAY_NEUTRAL }
                    if (evaluations.isNotEmpty()) {
                        mainHandler.post {
                            if (app.overlayPanel.isCardCurrentlyVisible()) {
                                app.overlayPanel.updateRatingsWithoutReset(evaluations)
                            }
                        }
                    }
                }
            }
        }

        if (validOffers.isEmpty()) {
            return
        }

        // Se o card já está visível para a MESMA corrida, não recria para evitar flicker.
        // Mas se a tela mudou para uma NOVA corrida, atualiza o card imediatamente sem travar!
        if (app.overlayPanel.isCardCurrentlyVisible()) {
            val activeFp = app.overlayPanel.getActiveCardFingerprint()
            val hasDifferentOffer = validOffers.any { offer ->
                val price = offer.offeredPrice ?: 0.0
                val km = offer.totalDistanceKmExplicit ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
                val fp = "${offer.sourceApp.name}|${String.format(Locale.US, "%.2f", price)}|${String.format(Locale.US, "%.1f", km)}"
                fp != activeFp
            }
            if (!hasDifferentOffer) {
                return
            }
        }

        lastOfferProcessedTimeMillis = now
        serviceScope.launch {
            val config = app.preferencesRepository.getInMemoryConfig()
            val evaluations = validOffers
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

            recentlyDisplayedOffers = evaluations.map { it.offer }
            recentlyDisplayedAtMillis = System.currentTimeMillis()
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

            // OCR da NOTA roda uma única vez por nova avaliação exibida.
            // Não compete com o scan atual nem precisa ler de novo preço/tempo/km.
            val ratingOfferKey = evaluations.joinToString("|") { buildStableOfferKey(it.offer) }
            if (evaluations.any {
                    it.offer.sourceApp == SourceApp.NINETY_NINE_DRIVER &&
                    it.offer.passengerRating == null && !it.offer.passengerIsNew
                } && (ratingOfferKey != lastRatingOcrOfferKey ||
                    System.currentTimeMillis() - lastRatingOcrAttemptMillis >= 1_500L)
            ) {
                val requestAt = System.currentTimeMillis()
                lastRatingOcrAttemptMillis = requestAt
                lastRatingOcrOfferKey = ratingOfferKey
                mainHandler.post {
                    val activePkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Throwable) { null }
                    val driverStillActive = activePkg != null &&
                        SourceApp.fromPackageName(activePkg) == SourceApp.NINETY_NINE_DRIVER
                    val ownOverlayFocused = activePkg == packageName &&
                        lastKnownForegroundPkg.let { SourceApp.fromPackageName(it) == SourceApp.NINETY_NINE_DRIVER } &&
                        app.overlayPanel.isCardCurrentlyVisible()
                    if ((driverStillActive || ownOverlayFocused) &&
                        recentlyDisplayedAtMillis >= requestAt - 2_500L &&
                        app.overlayPanel.isCardCurrentlyVisible()
                    ) {
                        triggerAutomaticAccessibilityOcrIfNeeded(
                            SourceApp.NINETY_NINE_DRIVER,
                            if (driverStillActive) activePkg!! else lastKnownForegroundPkg,
                            requestAt, ratingOnly = true
                        )
                    }
                }
            }
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

                    if (!offer.isSimulated && isLegitOffer &&
                        buildStableOfferKey(offer) !in alreadyShownBeforeList) {
                        val dao = app.database.rideOfferDao()
                        val recentOffers = dao.findRecentOffers(offer.sourceApp.displayName, now - 25_000L)
                        val duplicate = recentOffers.firstOrNull { existing ->
                            val priceDiff = kotlin.math.abs(existing.offeredPrice - (offer.offeredPrice ?: 0.0))
                            val kmDiff = kotlin.math.abs((existing.totalDistanceKm ?: 0.0) - totalKm)
                            priceDiff < 0.20 && (kmDiff < 0.35 || existing.totalDistanceKm == null)
                        }

                        if (duplicate != null) {
                            val needsEnrichment = duplicate.originAddress.isNullOrBlank() && !offer.originAddress.isNullOrBlank() ||
                                duplicate.destinationAddress.isNullOrBlank() && !offer.destinationAddress.isNullOrBlank()
                            if (needsEnrichment) {
                                val enriched = duplicate.copy(
                                    originAddress = if (!offer.originAddress.isNullOrBlank()) offer.originAddress else duplicate.originAddress,
                                    destinationAddress = if (!offer.destinationAddress.isNullOrBlank()) offer.destinationAddress else duplicate.destinationAddress,
                                    totalDistanceKm = if (duplicate.totalDistanceKm == null || duplicate.totalDistanceKm == 0.0) totalKm else duplicate.totalDistanceKm,
                                    totalDurationMin = if (duplicate.totalDurationMin == null || duplicate.totalDurationMin == 0) totalMin else duplicate.totalDurationMin
                                )
                                dao.updateOffer(enriched)
                            }
                        } else {
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
                            dao.insertOffer(entity)
                        }
                    }
                }
            }
        }
    }

    private fun mergeSameRowNodes(nodes: List<PositionedText>): List<String> {
        return PositionedTextRowMerger.merge(nodes.map {
            PositionedTextRowMerger.Fragment(it.text, it.centerY, it.leftX)
        })
    }

    private fun collectPositionedTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<PositionedText>,
        depth: Int,
        requireVisible: Boolean = true
    ) {
        if (node == null || depth > 50 || output.size >= 600) return
        try {
            // Verifica apenas limites na tela física

            val rect = Rect()
            try {
                node.getBoundsInScreen(rect)
            } catch (_: Throwable) {
            }

            val hasValidBounds = rect.width() > 0 && rect.height() > 0
            val displayMetrics = resources.displayMetrics
            val screenW = displayMetrics.widthPixels
            val screenH = displayMetrics.heightPixels
            val isOffScreen = hasValidBounds && (rect.bottom < 0 || rect.top > screenH || rect.right < 0 || rect.left > screenW)

            val txt = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val hasText = txt != null || desc != null

            // Se o nó possui texto e está no campo de visão, extrai os textos
            if (hasText && !isOffScreen && (!requireVisible || node.isVisibleToUser || !hasValidBounds)) {
                val centerY = if (hasValidBounds) (rect.top + rect.bottom) / 2 else 0
                val topY = if (hasValidBounds) rect.top else 0
                val leftX = if (hasValidBounds) rect.left else 0

                txt?.let {
                    output.add(PositionedText(it, topY, leftX, centerY))
                }
                desc?.let { d ->
                    if (output.none { it.text == d && kotlin.math.abs(it.centerY - centerY) <= 14 }) {
                        output.add(PositionedText(d, topY, leftX, centerY))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                        if (output.none { it.text == hint }) {
                            output.add(PositionedText(hint, topY, leftX, centerY))
                        }
                    }
                }
            }

            // SEMPRE percorre os nós filhos recursivamente, mesmo que o container pai tenha dimensões 0
            // ou não tenha texto próprio (exceto se o container estiver comprovadamente fora da tela física rolada)
            if (!isOffScreen) {
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

    private fun checkIfOfferDismissedAndCloseCard(activeRootPkg: String?, eventPkg: String?) {
        val app = applicationContext as? GoOrNoApplication ?: return
        if (!app.overlayPanel.isCardCurrentlyVisible()) return

        val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        val targetPkg = activeRoot?.packageName?.toString()
            ?: activeRootPkg
            ?: eventPkg
            ?: ""

        // Se o usuário saiu do app de motorista (foi pro WhatsApp, tela inicial, etc.), fecha o card imediatamente
        if (targetPkg.isNotBlank() && !SourceApp.isAllowedDriverPackage(targetPkg)) {
            app.overlayPanel.hideOfferCardOnly()
            return
        }

        // Se está no app de motorista, coleta os textos visíveis rápidos
        val texts = mutableListOf<String>()
        if (activeRoot != null) {
            collectFastTexts(activeRoot, texts, maxNodes = 50)
        }

        if (texts.isEmpty()) return

        val targetApp = SourceApp.fromPackageName(targetPkg)
        val visibleOfferApp = app.overlayPanel.getVisibleOfferSourceApp()
        if (visibleOfferApp != null && targetApp != SourceApp.UNKNOWN && visibleOfferApp != targetApp) {
            app.overlayPanel.hideOfferCardOnly()
            return
        }
        val screenContext = RideScreenContextClassifier.classify(targetApp, texts)
        if (screenContext == RideScreenContext.UBER_RADAR_LIST ||
            screenContext == RideScreenContext.NINETYNINE_REQUEST_LIST ||
            screenContext == RideScreenContext.NO_OFFER
        ) {
            app.overlayPanel.hideOfferCardOnly()
            return
        }

        val screenJoined = texts.joinToString(" ").lowercase(Locale.ROOT)

        // Se não tem nenhum "R$" na tela E não tem nenhum botão de ação ("aceitar", "escolher", "selecionar", "exclusivo", "tenho interesse"),
        // significa que a oferta sumiu/expirou e o motorista está no mapa ocioso!
        val hasCurrency = screenJoined.contains("r$")
        val hasRideAction = screenJoined.contains("aceitar") ||
            screenJoined.contains("escolher") ||
            screenJoined.contains("selecionar") ||
            screenJoined.contains("exclusivo") ||
            screenJoined.contains("deslize") ||
            screenJoined.contains("tenho interesse")

        val isIdleMap = screenJoined.contains("buscando solicitações") ||
            screenJoined.contains("buscando solicitacoes") ||
            screenJoined.contains("procurando viagens") ||
            screenJoined.contains("você está online") ||
            screenJoined.contains("voce esta online") ||
            screenJoined.contains("mapa de chamadas")

        if ((!hasCurrency && !hasRideAction) || isIdleMap) {
            app.overlayPanel.hideOfferCardOnly()
        }
    }

    private fun collectFastTexts(node: AccessibilityNodeInfo, list: MutableList<String>, maxNodes: Int) {
        if (list.size >= maxNodes) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty()) list.add(t)
        val cd = node.contentDescription?.toString()?.trim()
        if (!cd.isNullOrEmpty()) list.add(cd)

        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            collectFastTexts(child, list, maxNodes)
            try { child.recycle() } catch (_: Exception) {}
            if (list.size >= maxNodes) break
        }
    }

    


}
