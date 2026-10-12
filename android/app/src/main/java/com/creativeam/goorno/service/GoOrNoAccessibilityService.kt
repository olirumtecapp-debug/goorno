package com.creativeam.goorno.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.detector.ForegroundAppDetector
import com.creativeam.goorno.diagnostics.AccessibilitySnapshot
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
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
 * Examina os elementos de texto visíveis na janela ativa da Uber Driver e 99 Motorista,
 * aplica deduplicação, executa o Motor Financeiro e atualiza o Painel Flutuante.
 * Jamais executa cliques automáticos.
 */
class GoOrNoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastProcessedOffer: StandardRideOffer? = null
    private var lastEventTimeMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeDiagnosticsBus.setAccessibilityConnected(true)
        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: return

        // Ignora eventos do próprio GoOrNo para evitar loops
        if (pkgName == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastEventTimeMillis < 250L) return
        lastEventTimeMillis = now

        val detectedApp = ForegroundAppDetector.identifyForegroundApp(pkgName)
        val rootNode = rootInActiveWindow ?: return

        val extractedTexts = mutableListOf<String>()
        collectVisibleTexts(rootNode, extractedTexts, depth = 0)

        // Registra o snapshot no barramento de diagnóstico para inspeção na Tela 9
        RuntimeDiagnosticsBus.recordAccessibilitySnapshot(
            AccessibilitySnapshot(
                timestampMillis = now,
                packageName = pkgName,
                detectedApp = detectedApp,
                nodeCount = extractedTexts.size,
                extractedTexts = extractedTexts.take(80)
            )
        )

        // Processa apenas quando o aplicativo em primeiro plano é Uber Driver ou 99 Motorista
        if (detectedApp != SourceApp.UBER_DRIVER && detectedApp != SourceApp.NINETY_NINE_DRIVER) {
            return
        }

        val parsedOffer = when (detectedApp) {
            SourceApp.UBER_DRIVER -> UberOfferReader.parseScreenNodes(
                rawNodes = extractedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            SourceApp.NINETY_NINE_DRIVER -> NinetyNineOfferReader.parseScreenNodes(
                rawNodes = extractedTexts,
                captureMethod = CaptureMethod.ACCESSIBILITY,
                timestampMillis = now,
                isSimulated = false
            )
            else -> return
        }

        if (parsedOffer.screenClassification == ScreenClassification.NON_OFFER ||
            parsedOffer.screenClassification == ScreenClassification.ONGOING_TRIP
        ) {
            return
        }

        // Verifica deduplicação para evitar recalcular e gravar repetidamente a mesma oferta
        val dupCheck = BrazilianOfferParser.isSameOrUpdatedOffer(lastProcessedOffer, parsedOffer)
        if (dupCheck == BrazilianOfferParser.DuplicateCheckResult.EXACT_DUPLICATE) {
            return
        }

        val finalOffer = if (dupCheck == BrazilianOfferParser.DuplicateCheckResult.UPDATED_OFFER) {
            parsedOffer.copy(screenClassification = ScreenClassification.UPDATED_OFFER)
        } else {
            parsedOffer
        }
        lastProcessedOffer = finalOffer

        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.OFFER_DETECTED)

        val app = applicationContext as? GoOrNoApplication ?: return
        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluation = FinancialEngine.evaluate(finalOffer, config)

            RuntimeDiagnosticsBus.publishEvaluation(evaluation)
            app.overlayPanel.applyConfig(config)
            app.overlayPanel.showEvaluation(evaluation)

            // Salva no histórico local Room (somente ofertas reais e se habilitado pelo motorista)
            if (config.saveHistoryEnabled && !finalOffer.isSimulated && finalOffer.offeredPrice != null) {
                val entity = RideOfferEntity(
                    id = finalOffer.offerId,
                    timestampMillis = finalOffer.timestampMillis,
                    sourceApp = finalOffer.sourceApp.displayName,
                    captureMethod = finalOffer.captureMethod.label,
                    rideCategory = finalOffer.rideCategory,
                    offeredPrice = finalOffer.offeredPrice ?: 0.0,
                    actualReceivedPrice = null,
                    pickupDistanceKm = finalOffer.pickupDistanceKm,
                    tripDistanceKm = finalOffer.tripDistanceKm,
                    totalDistanceKm = evaluation.totalConsideredDistanceKm,
                    pickupDurationMin = finalOffer.pickupDurationMin,
                    tripDurationMin = finalOffer.tripDurationMin,
                    totalDurationMin = evaluation.totalConsideredDurationMin,
                    grossPerKm = evaluation.grossPerKm,
                    grossPerHour = evaluation.grossPerHour,
                    estimatedTotalCost = evaluation.estimatedTotalCost,
                    estimatedNetProfit = evaluation.estimatedNetProfit,
                    netProfitPerKm = evaluation.netProfitPerKm,
                    netProfitPerHour = evaluation.netProfitPerHour,
                    verdict = evaluation.verdict.name,
                    reasonsSummary = (evaluation.reasons + evaluation.warnings).joinToString(" | "),
                    originAddress = finalOffer.originAddress,
                    destinationAddress = finalOffer.destinationAddress,
                    confidenceScore = finalOffer.confidenceScore,
                    isPartialReading = !evaluation.isAnalysisComplete,
                    isSimulated = false
                )
                app.database.rideOfferDao().insertOffer(entity)
            }
        }
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>,
        depth: Int
    ) {
        if (node == null || depth > 28 || output.size >= 120) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            output.add(it)
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && !output.contains(it) }?.let {
            output.add(it)
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectVisibleTexts(child, output, depth + 1)
            }
        }
    }

    override fun onInterrupt() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
    }

    override fun onDestroy() {
        RuntimeDiagnosticsBus.setAccessibilityConnected(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}
