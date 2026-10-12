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
 * Serviço principal de leitura automática de ofertas (Android AccessibilityService) v2.6.0.
 *
 * Melhorias v2.6.0:
 * 1. ZERO janela flutuante em espera: nada fica flutuando na tela enquanto o motorista trabalha.
 *    O status ativo fica apenas no ícone da barra superior do Android (ao lado do relógio e do ícone da 99),
 *    com o botão "Desativar GoOrNo" direto na notificação.
 * 2. Abertura 100% automática quando toca corrida na 99/Uber ou quando o motorista abre a lista de "Solicitações"
 *    ou clica no ícone do "mapinha" para ver a viagem em detalhes (via Acessibilidade + OCR silencioso automático).
 * 3. NUNCA fica reabrindo de forma persistente a mesma corrida depois que o motorista fecha o card ou o tempo acaba!
 *    Cada oferta é mostrada 1 única vez por contexto (Lista vs Mapa Detalhado), e fecha com 1 toque em qualquer parte do card.
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    // Memória de ofertas já exibidas/fechadas no contexto atual ("LIST_VIEW|preco|km" ou "DETAIL_VIEW|preco|km")
    // Impede que a mesma oferta fique reabrindo sozinha e incomodando o motorista!
    private val shownOfferTimestamps = LinkedHashMap<String, Long>()
    private var lastScreenContextMode: String = ""
    private var lastKnownForegroundPkg: String = ""
    private var lastScanTimeMillis: Long = 0L
    private var lastAutoScreenshotTimeMillis: Long = 0L
    private var lastCardClosedTimeMillis: Long = 0L
    private var isTakingAutoScreenshot: Boolean = false

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

        app?.overlayPanel?.onOfferCardClosed = {
            lastCardClosedTimeMillis = System.currentTimeMillis()
        }

        serviceScope.launch {
            val cfg = app?.preferencesRepository?.getConfigOnce()
            val enabled = cfg?.copilotMonitoringEnabled ?: true
            app?.overlayPanel?.setMonitoringEnabled(enabled, accessibilityEnabled = true)
            if (enabled) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                CopilotNotificationReceiver.showActiveNotification(this@GoOrNoAccessibilityService)
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
                delay(950L)
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
            lastKnownForegroundPkg = evtPkg
        }

        // Se a janelinha de cálculo já está aberta na tela, não re-escaneia até ela fechar
        if (app?.overlayPanel?.isCardCurrentlyVisible() == true) {
            return
        }

        scanCurrentScreen(eventPkg = evtPkg, forceThrottleBypass = false)
    }

    private fun scanCurrentScreen(eventPkg: String?, forceThrottleBypass: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceThrottleBypass && now - lastScanTimeMillis < 260L) return
        // Pequena pausa de 1.2s logo após o motorista fechar manualmente o card para nunca reabrir durante a animação de saída
        if (now - lastCardClosedTimeMillis < 1200L) return
        lastScanTimeMillis = now

        val candidateWindows = mutableListOf<Pair<String, AccessibilityNodeInfo>>()

        // 1. Coleta todas as janelas visíveis na tela (ignorando o próprio GoOrNo e SystemUI)
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
                val activePkg = activeRoot.packageName?.toString() ?: eventPkg ?: lastKnownForegroundPkg
                if (activePkg.isNotBlank() && activePkg != packageName && activePkg != "com.android.systemui") {
                    if (candidateWindows.none { it.second == activeRoot }) {
                        candidateWindows.add(0, Pair(activePkg, activeRoot))
                    }
                }
            }
        } catch (_: Exception) {
        }

        val rideWindow = candidateWindows.firstOrNull { (pkg, _) ->
            SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN
        }
        val targetPkg = rideWindow?.first
            ?: candidateWindows.firstOrNull()?.first
            ?: eventPkg?.takeIf { it.isNotBlank() && it != packageName && it != "com.android.systemui" }
            ?: lastKnownForegroundPkg

        if (targetPkg.isBlank() || targetPkg == packageName) return

        val positionedNodes = mutableListOf<PositionedText>()
        for ((pkg, rootNode) in candidateWindows) {
            if (rideWindow == null || pkg == targetPkg || SourceApp.fromPackageName(pkg) != SourceApp.UNKNOWN) {
                collectPositionedTexts(rootNode, positionedNodes, depth = 0)
            }
        }

        val mergedLines = mergeSameRowNodes(positionedNodes)
        val normalizedTexts = BrazilianOfferParser.normalizeFragmentedNodes(mergedLines)
        val detectedApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedTexts)

        // Atualiza o Inspetor na Tela 9 para diagnóstico em tempo real
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
        val isRideApp = detectedApp == SourceApp.UBER_DRIVER ||
            detectedApp == SourceApp.NINETY_NINE_DRIVER ||
            isKnownRidePackage

        val effectiveApp = when {
            detectedApp != SourceApp.UNKNOWN -> detectedApp
            isKnownRidePackage -> SourceApp.fromPackageName(targetPkg)
            else -> SourceApp.UNKNOWN
        }

        // 1º Tentativa: extrair ofertas diretamente pelos nós de Acessibilidade
        val parsedOffers = if (effectiveApp != SourceApp.UNKNOWN) {
            extractOffersForApp(effectiveApp, normalizedTexts, CaptureMethod.ACCESSIBILITY, now)
        } else {
            emptyList()
        }

        if (parsedOffers.isNotEmpty()) {
            processAndShowOffers(parsedOffers, normalizedTexts, now)
            return
        }

        // 2º Tentativa Automática (Leitura Visual Silenciosa via takeScreenshot + ML Kit OCR):
        // Acionada sempre que a 99 ou Uber estiver na tela (mesmo que esconda os nós e retorne 0 ou 2 itens)
        // OU quando o motorista estiver testando um print na Galeria/Fotos!
        val isGalleryOrPhotoViewer = targetPkg.contains("gallery", ignoreCase = true) ||
            targetPkg.contains("photos", ignoreCase = true) ||
            targetPkg.contains("media", ignoreCase = true)

        if (isRideApp || isGalleryOrPhotoViewer) {
            val fallbackApp = if (effectiveApp != SourceApp.UNKNOWN) effectiveApp else SourceApp.NINETY_NINE_DRIVER
            triggerAutomaticAccessibilityOcrIfNeeded(fallbackApp, targetPkg, now)
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
     * Captura silenciosa via AccessibilityService.takeScreenshot (Android 11 / API 30+)
     * e leitura de todos os preços, km, metros, minutos e endereços via ML Kit OCR local —
     * abrindo a janelinha de cálculo SOZINHO sem nenhuma barrinha flutuante em espera!
     */
    private fun triggerAutomaticAccessibilityOcrIfNeeded(
        targetApp: SourceApp,
        targetPkg: String,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (isTakingAutoScreenshot) return
        val app = applicationContext as? GoOrNoApplication
        if (app?.overlayPanel?.isCardCurrentlyVisible() == true) return
        if (now - lastAutoScreenshotTimeMillis < 1050L) return

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
                                            !txt.contains("COMPENSA", ignoreCase = true) &&
                                            !txt.contains("Toque em qualquer parte", ignoreCase = true) &&
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
                                    val inferredApp = BrazilianOfferParser.inferSourceAppFromScreenContent(targetPkg, normalizedOcr)
                                    val effectiveOcrApp = when {
                                        inferredApp != SourceApp.UNKNOWN -> inferredApp
                                        targetApp != SourceApp.UNKNOWN -> targetApp
                                        else -> SourceApp.NINETY_NINE_DRIVER
                                    }

                                    RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
                                        AccessibilitySnapshot(
                                            timestampMillis = System.currentTimeMillis(),
                                            packageName = "$targetPkg (Leitura Visual Automática)",
                                            detectedApp = effectiveOcrApp,
                                            nodeCount = normalizedOcr.size,
                                            extractedTexts = normalizedOcr.take(90)
                                        )
                                    )

                                    var ocrOffers = extractOffersForApp(
                                        appType = effectiveOcrApp,
                                        lines = normalizedOcr,
                                        method = CaptureMethod.ACCESSIBILITY,
                                        timestampMillis = System.currentTimeMillis()
                                    )

                                    // Fallback cruzado caso um print seja de Uber ou 99 sem pacote explícito
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

    /**
     * Cria uma chave estável por oferta (baseada apenas em modo de tela + preço + distância arredondada),
     * garantindo que depois que o motorista fechar a janelinha (ou o tempo acabar), a MESMA corrida
     * NUNCA fique reabrindo sozinha!
     * Porém, se o motorista clicar no ícone do "mapinha" na lista de Solicitações da 99, o modo muda
     * de LIST_VIEW para DETAIL_VIEW e o app mostra o cálculo 1 vez para a tela detalhada do mapa!
     */
    private fun buildStableOfferKey(screenMode: String, offer: StandardRideOffer): String {
        val priceKey = offer.offeredPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"
        val totalKm = offer.totalDistanceKmExplicit
            ?: ((offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 0.0))
        val kmKey = String.format(Locale.US, "%.1f", totalKm)
        return "$screenMode|$priceKey|$kmKey"
    }

    private fun processAndShowOffers(
        parsedOffers: List<StandardRideOffer>,
        screenLines: List<String>,
        now: Long
    ) {
        if (parsedOffers.isEmpty()) return

        // Identifica se estamos na tela de Lista ("Solicitações / Escolha uma") ou na tela de Detalhe do Mapa / Tocando
        val joinedLower = screenLines.joinToString(" ").lowercase(Locale.getDefault())
        val currentScreenMode = when {
            joinedLower.contains("solicitações") ||
                joinedLower.contains("solicitacoes") ||
                joinedLower.contains("escolha uma") -> "LIST_VIEW"
            else -> "DETAIL_VIEW"
        }

        // Se o motorista estava na Lista de Solicitações ("LIST_VIEW") e clicou no ícone do mapinha para abrir
        // os detalhes da viagem ("DETAIL_VIEW"), limpamos o histórico de "DETAIL_VIEW" para exibir o cálculo da viagem clicada 1 vez!
        if (lastScreenContextMode == "LIST_VIEW" && currentScreenMode == "DETAIL_VIEW") {
            val detailKeys = shownOfferTimestamps.keys.filter { it.startsWith("DETAIL_VIEW|") }
            detailKeys.forEach { shownOfferTimestamps.remove(it) }
        }
        lastScreenContextMode = currentScreenMode

        // Limpa chaves antigas (> 75 segundos)
        val expiredKeys = shownOfferTimestamps.entries
            .filter { now - it.value > 75_000L }
            .map { it.key }
        expiredKeys.forEach { shownOfferTimestamps.remove(it) }

        // Filtra: verifica se há pelo menos 1 oferta nova neste contexto de tela que ainda NÃO foi mostrada/fechada!
        val hasUnshownOfferInThisMode = parsedOffers.any { offer ->
            val key = buildStableOfferKey(currentScreenMode, offer)
            !shownOfferTimestamps.containsKey(key)
        }

        if (!hasUnshownOfferInThisMode) {
            // Todas as ofertas presentes nesta tela já foram mostradas (e fechadas pelo motorista ou pelo timer).
            // Não reabre a janela para não incomodar!
            return
        }

        // Registra todas as ofertas atuais como já mostradas neste modo de tela
        for (offer in parsedOffers) {
            val key = buildStableOfferKey(currentScreenMode, offer)
            shownOfferTimestamps[key] = now
        }

        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

        val app = applicationContext as? GoOrNoApplication ?: return
        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluations = parsedOffers.map { offer ->
                FinancialEngine.evaluate(offer, config)
            }

            // Abre a janelinha flutuante AUTOMATICAMENTE (1 única vez por oferta/contexto!)
            RuntimeDiagnosticsBus.publishEvaluation(evaluations.first())
            app.overlayPanel.applyConfig(config)
            app.overlayPanel.showEvaluations(evaluations)
            CopilotNotificationReceiver.showActiveNotification(
                this@GoOrNoAccessibilityService,
                evaluations.first()
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

    override fun onInterrupt() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.hidePanel()
        CopilotNotificationReceiver.cancelActiveNotification(this)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        (applicationContext as? GoOrNoApplication)?.overlayPanel?.hidePanel()
        CopilotNotificationReceiver.cancelActiveNotification(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
