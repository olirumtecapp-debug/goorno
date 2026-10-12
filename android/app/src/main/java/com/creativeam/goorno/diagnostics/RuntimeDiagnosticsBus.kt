package com.creativeam.goorno.diagnostics

import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.SourceApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CopilotRuntimeState(val label: String) {
    SERVICE_OFF("Serviço Desligado"),
    PERMISSION_MISSING("Permissão Ausente"),
    READING_IN_PROGRESS("Monitorando Tela (Uber / 99)"),
    OFFER_DETECTED("Oferta Detectada"),
    ANALYSIS_COMPLETED("Análise Concluída"),
    READ_ERROR("Leitura Incompleta / Erro")
}

data class AccessibilitySnapshot(
    val timestampMillis: Long = 0L,
    val packageName: String = "Nenhum",
    val detectedApp: SourceApp = SourceApp.UNKNOWN,
    val nodeCount: Int = 0,
    val extractedTexts: List<String> = emptyList()
)

/**
 * Barramento reativo em memória para sincronizar o estado da Acessibilidade, OCR,
 * Painel Flutuante e Tela de Diagnóstico em tempo real.
 */
object RuntimeDiagnosticsBus {

    private val _runtimeState = MutableStateFlow(CopilotRuntimeState.SERVICE_OFF)
    val runtimeState: StateFlow<CopilotRuntimeState> = _runtimeState.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    private val _ocrActive = MutableStateFlow(false)
    val ocrActive: StateFlow<Boolean> = _ocrActive.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val _lastSnapshot = MutableStateFlow(AccessibilitySnapshot())
    val lastSnapshot: StateFlow<AccessibilitySnapshot> = _lastSnapshot.asStateFlow()

    private val _lastRealEvaluation = MutableStateFlow<FinancialEvaluationResult?>(null)
    val lastRealEvaluation: StateFlow<FinancialEvaluationResult?> = _lastRealEvaluation.asStateFlow()

    private val _lastSimulatedEvaluation = MutableStateFlow<FinancialEvaluationResult?>(null)
    val lastSimulatedEvaluation: StateFlow<FinancialEvaluationResult?> = _lastSimulatedEvaluation.asStateFlow()

    fun updateRuntimeState(state: CopilotRuntimeState) {
        _runtimeState.value = state
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _accessibilityConnected.value = connected
        if (!connected && !_ocrActive.value) {
            _runtimeState.value = CopilotRuntimeState.SERVICE_OFF
        } else if (connected && _runtimeState.value == CopilotRuntimeState.SERVICE_OFF) {
            _runtimeState.value = CopilotRuntimeState.READING_IN_PROGRESS
        }
    }

    fun setOcrActive(active: Boolean) {
        _ocrActive.value = active
    }

    fun setOverlayVisible(visible: Boolean) {
        _overlayVisible.value = visible
    }

    fun recordAccessibilitySnapshot(snapshot: AccessibilitySnapshot) {
        _lastSnapshot.value = snapshot
    }

    fun publishEvaluation(result: FinancialEvaluationResult) {
        if (result.offer.isSimulated) {
            _lastSimulatedEvaluation.value = result
        } else {
            _lastRealEvaluation.value = result
        }
        _runtimeState.value = if (result.isAnalysisComplete) {
            CopilotRuntimeState.ANALYSIS_COMPLETED
        } else {
            CopilotRuntimeState.READ_ERROR
        }
    }
}
