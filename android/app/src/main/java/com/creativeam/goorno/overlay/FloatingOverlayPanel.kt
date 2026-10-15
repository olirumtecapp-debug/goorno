package com.creativeam.goorno.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.model.DriverFinancialConfig
import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.OverlayDisplayStyle
import com.creativeam.goorno.model.TrafficLightVerdict
import java.util.Locale
import kotlin.math.abs

/**
 * Painel Flutuante Nativo v2.8.0 — Livremente Arrastável na Tela + Semáforo Otimizado + Lucro/Custo Opcionais.
 *
 * - TOTALMENTE ARRASTÁVEL: O motorista pode segurar e arrastar a janelinha para qualquer lugar da tela
 *   (meio, lateral, embaixo) para não atrapalhar o botão de recusar/desistir de um chamado da Uber ou 99.
 *   A posição escolhida fica salva automaticamente para todas as próximas chamadas!
 * - LUCRO E CUSTO OPCIONAIS (Ocultos por padrão na janela flutuante, ativáveis em "2. Janela").
 * - ZERO botões de Origem/Destino na janela flutuante (ficam apenas na Notificação do Topo e no Histórico).
 * - Quando há várias ofertas na tela (ex: Lista de Solicitações da 99), mostra 1 por vez (mostra -> apaga -> mostra a outra).
 */
class FloatingOverlayPanel(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val posPrefs = context.getSharedPreferences("goorno_overlay_position", Context.MODE_PRIVATE)

    private var rootContainer: LinearLayout? = null
    private var contentWrapper: LinearLayout? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false
    private var isAccessibilityActive = false

    private var offerQueue: List<FinancialEvaluationResult> = emptyList()
    private var currentQueueIndex: Int = 0
    private var isTransitioningBetweenOffers: Boolean = false

    private var currentOpacityPercent: Int = 92
    private var displayDurationSec: Int = 7
    private var displayStyle: OverlayDisplayStyle = OverlayDisplayStyle.SEMAFORO_COMPACT
    private var showProfitOnOverlay: Boolean = false
    private var showCostOnOverlay: Boolean = false
    private var remainingSeconds: Int = 7
    private var isMonitoringEnabled: Boolean = true
    private var activeTimerLabel: TextView? = null

    var onUserRequestedDisableCopilot: (() -> Unit)? = null
    var onOfferCardClosed: (() -> Unit)? = null

    private val countdownRunnable = object : Runnable {
        override fun run() {
            remainingSeconds--
            if (remainingSeconds <= 0) {
                advanceToNextOfferOrFinish()
            } else {
                activeTimerLabel?.text = "${remainingSeconds}s"
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val showNextOfferAfterPauseRunnable = Runnable {
        isTransitioningBetweenOffers = false
        showCurrentQueuedOffer()
    }

    private var activeCardFingerprint: String = ""
    private val recentlyDismissedOfferTimestamps = mutableMapOf<String, Long>()

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun setMonitoringEnabled(enabled: Boolean, accessibilityEnabled: Boolean = isAccessibilityActive) {
        isMonitoringEnabled = enabled
        isAccessibilityActive = accessibilityEnabled
        if (!enabled) {
            hidePanel()
        }
    }

    fun isCopilotMonitoringEnabled(): Boolean = isMonitoringEnabled

    fun isCardCurrentlyVisible(): Boolean = isAdded || isTransitioningBetweenOffers

    fun applyConfig(config: DriverFinancialConfig) {
        mainHandler.post {
            currentOpacityPercent = config.overlayOpacityPercent.coerceIn(25, 100)
            displayDurationSec = config.overlayAutoMinimizeSec.coerceIn(3, 45)
            displayStyle = OverlayDisplayStyle.fromName(config.overlayDisplayStyle)
            showProfitOnOverlay = config.showProfitOnOverlay
            showCostOnOverlay = config.showCostOnOverlay
            isMonitoringEnabled = config.copilotMonitoringEnabled
            rootContainer?.alpha = currentOpacityPercent / 100f
            if (!isMonitoringEnabled) {
                hidePanel()
            }
        }
    }

    /**
     * Volta a janela flutuante para a posição inicial padrão (abaixo do topo, sem cobrir botões).
     */
    fun resetSavedPosition() {
        posPrefs.edit().clear().apply()
        mainHandler.post {
            layoutParams?.let { lp ->
                lp.x = 0
                lp.y = dp(90)
                if (isAdded && rootContainer != null) {
                    try {
                        windowManager.updateViewLayout(rootContainer, lp)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun showOrUpdateTopStatusBar(accessibilityEnabled: Boolean, statusSubtext: String? = null) {
        isAccessibilityActive = accessibilityEnabled
        if (!isMonitoringEnabled) {
            hidePanel()
        }
    }

    fun showEvaluation(result: FinancialEvaluationResult) {
        showEvaluations(listOf(result))
    }

    /**
     * Exibe 1 ou mais ofertas em fila sequencial (mostra uma -> apaga -> mostra a próxima -> apaga),
     * sem nunca empilhar várias janelas uma embaixo da outra!
     */
    fun showEvaluations(results: List<FinancialEvaluationResult>) {
        if (results.isEmpty()) return
        mainHandler.post {
            if (!hasOverlayPermission()) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.PERMISSION_MISSING)
                return@post
            }
            if (!isMonitoringEnabled && results.none { it.offer.isSimulated }) {
                return@post
            }

            val firstEval = results.first()
            val newPrice = firstEval.offer.offeredPrice ?: 0.0
            val newKm = firstEval.totalConsideredDistanceKm ?: 0.0
            val newFingerprint = "${firstEval.offer.sourceApp.name}|${String.format(Locale.US, "%.2f", newPrice)}|${String.format(Locale.US, "%.1f", newKm)}"

            val now = System.currentTimeMillis()
            recentlyDismissedOfferTimestamps.entries.removeAll { now - it.value > 75_000L }

            // Se esse mesmo chamado foi exibido ou fechado nos últimos 75 segundos, NÃO reabre na tela!
            if (recentlyDismissedOfferTimestamps.containsKey(newFingerprint)) {
                return@post
            }

            // Proteção Anti-Flicker: se o card flutuante já está visível para a mesma corrida,
            // NÃO destrói nem recria as views (evita sumir o valor ou piscar a janela!)
            if (isCardCurrentlyVisible() && activeCardFingerprint == newFingerprint && offerQueue.isNotEmpty()) {
                return@post
            }
            activeCardFingerprint = newFingerprint

            mainHandler.removeCallbacks(countdownRunnable)
            mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
            isTransitioningBetweenOffers = false

            offerQueue = results.take(5)
            currentQueueIndex = 0
            showCurrentQueuedOffer()
        }
    }

    private fun showCurrentQueuedOffer() {
        if (currentQueueIndex >= offerQueue.size) {
            hidePanel()
            onOfferCardClosed?.invoke()
            return
        }

        val currentEval = offerQueue[currentQueueIndex]
        val totalInQueue = offerQueue.size
        val oneBasedIndex = currentQueueIndex + 1

        ensureViewsCreated()
        attachWindowIfNeeded()

        rootContainer?.alpha = currentOpacityPercent / 100f
        contentWrapper?.removeAllViews()

        remainingSeconds = if (totalInQueue > 1) {
            displayDurationSec.coerceIn(3, 5)
        } else {
            displayDurationSec
        }

        val builtView = when (displayStyle) {
            OverlayDisplayStyle.MINI_BALL -> buildMiniBallView(currentEval, oneBasedIndex, totalInQueue)
            OverlayDisplayStyle.SEMAFORO_COMPACT,
            OverlayDisplayStyle.FULL_CARD -> buildModernSpeechBubbleCardView(currentEval, oneBasedIndex, totalInQueue)
        }

        contentWrapper?.addView(builtView)

        mainHandler.removeCallbacks(countdownRunnable)
        mainHandler.postDelayed(countdownRunnable, 1000L)
    }

    private fun advanceToNextOfferOrFinish() {
        mainHandler.removeCallbacks(countdownRunnable)
        currentQueueIndex++
        if (currentQueueIndex < offerQueue.size) {
            removeWindowViewOnly()
            isTransitioningBetweenOffers = true
            mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
            mainHandler.postDelayed(showNextOfferAfterPauseRunnable, 350L)
        } else {
            hidePanel()
            onOfferCardClosed?.invoke()
        }
    }

    fun dismissCurrentOfferCardByUser() {
        activeCardFingerprint = ""
        offerQueue = emptyList()
        currentQueueIndex = 0
        isTransitioningBetweenOffers = false
        hidePanel()
        onOfferCardClosed?.invoke()
    }

    fun hideOfferCardOnly() {
        dismissCurrentOfferCardByUser()
    }

    private fun removeWindowViewOnly() {
        if (isAdded && rootContainer != null) {
            try {
                windowManager.removeView(rootContainer)
            } catch (_: Exception) {
            }
            isAdded = false
            RuntimeDiagnosticsBus.setOverlayVisible(false)
        }
    }

    fun hidePanel() {
        mainHandler.post {
            if (activeCardFingerprint.isNotBlank()) {
                recentlyDismissedOfferTimestamps[activeCardFingerprint] = System.currentTimeMillis()
            }
            activeCardFingerprint = ""
            mainHandler.removeCallbacks(countdownRunnable)
            mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
            isTransitioningBetweenOffers = false
            removeWindowViewOnly()
        }
    }

    private fun attachWindowIfNeeded() {
        if (!isAdded && rootContainer != null && layoutParams != null) {
            try {
                windowManager.addView(rootContainer, layoutParams)
                isAdded = true
                RuntimeDiagnosticsBus.setOverlayVisible(true)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildOptionalProfitAndCostSuffix(result: FinancialEvaluationResult): String {
        val parts = mutableListOf<String>()
        if (showProfitOnOverlay) {
            val netStr = result.estimatedNetProfit?.let { formatCurrency(it) } ?: "--"
            parts.add("Lucro: $netStr")
        }
        if (showCostOnOverlay) {
            val costStr = result.estimatedTotalCost?.let { formatCurrency(it) } ?: "--"
            parts.add("Custo: $costStr")
        }
        return if (parts.isEmpty()) "" else "  •  " + parts.joinToString("  •  ")
    }

    // =========================================================================
    // FORMATO 1: BOLINHA SEMÁFORO COMPACTA (Padrão Recomendado — Zero Poluição)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildSemaforoCompactView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val (badgeHex, _, _) = verdictPalette(result.verdict)

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedCardBg("#0A1916", badgeHex, 28f, 2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Ícone de alça (⠿) indicando que o motorista pode arrastar a janela
        val dragHandle = TextView(context).apply {
            text = "⠿"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, 0, dp(6), 0)
        }
        pill.addView(dragHandle)

        // Bolinha Luminosa do Semáforo (Verde / Amarela / Vermelha)
        val ball = View(context).apply {
            background = circleBg(badgeHex, "#FFFFFF", 2)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(10)
            }
        }
        pill.addView(ball)

        val priceStr = offer.offeredPrice?.let { formatCurrency(it) } ?: "R$ --"
        val distStr = result.totalConsideredDistanceKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
        } ?: "-- km"
        val minStr = result.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"
        val grossKmStr = result.grossPerKm?.let { formatCurrency(it) + "/km" } ?: "--/km"
        val grossHrStr = result.grossPerHour?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f/h", it)
        } ?: "--/h"
        val optionalSuffix = buildOptionalProfitAndCostSuffix(result)
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(10)
            }
        }

        val riskTitleTag = if (result.hasRiskAlert) " · ⚠️ ÁREA DE RISCO" else ""
        val tvLine1 = TextView(context).apply {
            text = "$seqTag$priceStr  •  ${result.verdict.label} ($distStr · $minStr)$riskTitleTag"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val ratingSegment = if (!result.passengerRatingBadge.isNullOrBlank()) {
            "${result.passengerRatingBadge}  •  "
        } else ""
        val riskPrefix = if (result.hasRiskAlert && !result.riskAlertBadge.isNullOrBlank()) {
            "${result.riskAlertBadge}  •  "
        } else ""
        val line2Color = when {
            result.hasRiskAlert -> "#FDE047"
            result.passengerRatingVerdict.name == "BAD" -> "#FCA5A5"
            result.passengerRatingVerdict.name == "MEDIUM" -> "#FDE047"
            else -> "#A7F3D0"
        }
        val tvLine2 = TextView(context).apply {
            text = "$riskPrefix$ratingSegment$grossKmStr  •  $grossHrStr$optionalSuffix"
            setTextColor(Color.parseColor(line2Color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
        }

        textCol.addView(tvLine1)
        textCol.addView(tvLine2)
        pill.addView(textCol)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(2), dp(6), dp(2))
        }
        activeTimerLabel = tvTimer
        pill.addView(tvTimer)

        val btnCloseAll = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBg("#DC2626", 14f)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        pill.addView(btnCloseAll)

        return pill
    }

    // =========================================================================
    // FORMATO 2: SÓ BOLINHA SEMÁFORO + TOTAL KM/TEMPO + R$/KM (Ultra Discreto)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildMiniBallView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val (badgeHex, _, _) = verdictPalette(result.verdict)

        val miniPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = roundedCardBg("#0A1916", badgeHex, 24f, 2)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
            setOnTouchListener(createDragOrTapTouchListener())
        }

        val dragHandle = TextView(context).apply {
            text = "⠿"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, dp(5), 0)
        }
        miniPill.addView(dragHandle)

        val ball = View(context).apply {
            background = circleBg(badgeHex, "#FFFFFF", 2)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(8)
            }
        }
        miniPill.addView(ball)

        val priceStr = offer.offeredPrice?.let { formatCurrency(it) } ?: "R$ --"
        val distStr = result.totalConsideredDistanceKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1fkm", it)
        } ?: "--km"
        val minStr = result.totalConsideredDurationMin?.let { "${it}m" } ?: "--m"
        val grossKmStr = result.grossPerKm?.let { formatCurrency(it) + "/km" } ?: "--/km"
        val grossHrStr = result.grossPerHour?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f/h", it)
        } ?: "--/h"
        val optionalSuffix = buildOptionalProfitAndCostSuffix(result)
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""
        val riskMiniTag = if (result.hasRiskAlert) "⚠️ RISCO · " else ""
        val ratingMiniTag = if (result.passengerRating != null) {
            val rStr = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating)
            "${result.passengerRatingVerdict.emoji}★$rStr · "
        } else ""

        val tvCompact = TextView(context).apply {
            text = "$seqTag$priceStr ($distStr · $minStr)  •  ${result.verdict.label}  •  $grossKmStr · $grossHrStr$optionalSuffix$riskMiniTag$ratingMiniTag"
            setTextColor(if (result.hasRiskAlert) Color.parseColor("#FDE047") else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, dp(8), 0)
        }
        miniPill.addView(tvCompact)

        activeTimerLabel = null

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FCA5A5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(4), dp(2))
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        miniPill.addView(btnClose)

        return miniPill
    }

    // =========================================================================
    // FORMATO OFICIAL: BALÃO DE VEREDITO (ACEITAR / ATENÇÃO / RECUSAR)
    // + CARD ESCURO MODERNO COM OS 4 PILARES E BARRAS VERTICAIS COLORIDAS (EXATO COMO PEDIDO)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildFullCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        return buildModernSpeechBubbleCardView(result, index, totalCount)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildModernSpeechBubbleCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer

        // Cores de veredito de alto contraste para o Balão Superior (Speech Bubble)
        val (verdictText, verdictBgHex, verdictTextColor) = when (result.verdict) {
            TrafficLightVerdict.GREEN -> Triple("✓  ACEITAR", "#16A34A", Color.WHITE)
            TrafficLightVerdict.YELLOW -> Triple("⚠  ATENÇÃO", "#EAB308", Color.parseColor("#0F172A"))
            TrafficLightVerdict.RED -> Triple("✕  RECUSAR", "#DC2626", Color.WHITE)
            TrafficLightVerdict.GRAY_NEUTRAL -> Triple("●  AVALIAR", "#475569", Color.WHITE)
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // 1. BALÃO DE FALA COM PONTA (SPEECH BUBBLE TRIANGLE)
        val speechBubblePill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(6), dp(18), dp(6))
            background = roundedBg(verdictBgHex, 20f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
        }

        val tvVerdict = TextView(context).apply {
            text = verdictText
            setTextColor(verdictTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
        }
        speechBubblePill.addView(tvVerdict)

        // Rabicho/Ponta do balão apontando para baixo diretamente para o card
        val speechTail = object : View(context) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(verdictBgHex)
                style = android.graphics.Paint.Style.FILL
            }
            private val path = android.graphics.Path()

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                path.reset()
                val w = width.toFloat()
                val h = height.toFloat()
                path.moveTo(0f, 0f)
                path.lineTo(w, 0f)
                path.lineTo(w / 2f, h)
                path.close()
                canvas.drawPath(path, paint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(8)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = -dp(1)
                bottomMargin = dp(2)
            }
        }

        val speechBubbleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(speechBubblePill)
            addView(speechTail)
        }
        rootLayout.addView(speechBubbleContainer)

        // 2. CARD ESCURO (DESIGN MODERNO, TRANSLÚCIDO E COM BORDA DA COR DO VEREDITO)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = roundedCardBg("#111F1A", verdictBgHex, 16f, 2)
            minimumWidth = dp(310)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // Subtítulo do Topo: ex "Uber · R$ 14,01 · 17 min · 6,3 km" + timer + botão fechar
        val distStr = result.totalConsideredDistanceKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
        } ?: "-- km"
        val minStr = result.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"
        val priceStr = offer.offeredPrice?.let { formatCurrency(it) } ?: ""
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""
        val subtitleContent = buildString {
            append(seqTag)
            append(offer.sourceApp.displayName)
            if (priceStr.isNotBlank()) append(" · $priceStr")
            append(" · $minStr · $distStr")
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val tvSubtitle = TextView(context).apply {
            text = subtitleContent
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvSubtitle)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = roundedBg("#1E293B", 8f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }
        activeTimerLabel = tvTimer
        topRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = roundedBg("#2A1215", 8f)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        topRow.addView(btnClose)
        card.addView(topRow)

        // 3. LINHA DOS 4 PILARES COM BARRAS VERTICAIS COLORIDAS
        val pillarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun addPillar(label: String, valueText: String, barColorHex: String) {
            val pillar = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                setSingleLine(true)
            }
            pillar.addView(tvLabel)

            val valueRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            }

            val verticalBar = View(context).apply {
                background = roundedBg(barColorHex, 2f)
                layoutParams = LinearLayout.LayoutParams(dp(3), dp(18)).apply {
                    marginEnd = dp(4)
                }
            }
            valueRow.addView(verticalBar)

            val tvValue = TextView(context).apply {
                text = valueText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setSingleLine(true)
            }
            valueRow.addView(tvValue)
            pillar.addView(valueRow)

            pillarsRow.addView(pillar)
        }

        // 1. Pilar: R$/Km
        val kmVal = result.grossPerKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", it)
        } ?: "--"
        val (kmBarColor, _, _) = verdictPalette(result.kmVerdict)
        addPillar("R$/Km", kmVal, kmBarColor)

        // 2. Pilar: R$/Hora
        val hrVal = result.grossPerHour?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.0f", it)
        } ?: "--"
        val (hrBarColor, _, _) = verdictPalette(result.hourVerdict)
        addPillar("R$/Hora", hrVal, hrBarColor)

        // 3. Pilar: Lucro (%) ou Lucro R$
        val netPercentVal = result.estimatedNetMarginPercent?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.0f%%", it)
        } ?: (result.estimatedNetProfit?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$%.0f", it) } ?: "--")
        val (profitBarColor, _, _) = verdictPalette(result.netVerdict)
        addPillar("Lucro", netPercentVal, profitBarColor)

        // 4. Pilar: Nota do Passageiro
        val ratingVal = result.passengerRating?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", it)
        } ?: "--"
        val ratingBarColor = when (result.passengerRatingVerdict) {
            PassengerRatingVerdict.GOOD -> "#22C55E"
            PassengerRatingVerdict.MEDIUM -> "#EAB308"
            PassengerRatingVerdict.BAD -> "#EF4444"
            PassengerRatingVerdict.UNKNOWN -> "#94A3B8"
        }
        addPillar("Nota", ratingVal, ratingBarColor)

        card.addView(pillarsRow)

        // Se houver alerta de Área de Risco:
        if (result.hasRiskAlert && !result.riskAlertBadge.isNullOrBlank()) {
            val tvRisk = TextView(context).apply {
                text = "⚠️ ÁREA DE RISCO: ${result.riskAlertBadge}"
                setTextColor(Color.parseColor("#FDE047"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBg("#450A0A", 6f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }
            card.addView(tvRisk)
        }

        rootLayout.addView(card)
        return rootLayout
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragOrTapTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var wasDragged = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        wasDragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > dp(5) || abs(dy) > dp(5)) {
                            wasDragged = true
                            lp.x = initialX + dx
                            lp.y = (initialY + dy).coerceAtLeast(0)
                            if (isAdded && rootContainer != null) {
                                try {
                                    windowManager.updateViewLayout(rootContainer, lp)
                                } catch (_: Exception) {
                                }
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (wasDragged) {
                            // Salva a nova posição escolhida pelo motorista para nunca mais atrapalhar o botão de desistir!
                            posPrefs.edit()
                                .putInt("saved_x", lp.x)
                                .putInt("saved_y", lp.y)
                                .apply()
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            advanceToNextOfferOrFinish()
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureViewsCreated() {
        if (rootContainer != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Posição inicial padrão: dp(90) abaixo do topo para não ficar em cima do botão "X / Recusar" do topo da 99/Uber,
        // ou na última posição exata para onde o motorista arrastou!
        val savedX = posPrefs.getInt("saved_x", 0)
        val savedY = posPrefs.getInt("saved_y", dp(90))

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = savedX
            y = savedY
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = currentOpacityPercent / 100f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener(createDragOrTapTouchListener())
        }

        contentWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        rootContainer?.addView(contentWrapper)
    }

    private fun verdictPalette(verdict: TrafficLightVerdict): Triple<String, String, String> {
        return when (verdict) {
            TrafficLightVerdict.GREEN -> Triple("#10B981", "#F0FDF4", "#86EFAC")
            TrafficLightVerdict.YELLOW -> Triple("#F59E0B", "#FFFBEB", "#FCD34D")
            TrafficLightVerdict.RED -> Triple("#EF4444", "#FEF2F2", "#FCA5A5")
            TrafficLightVerdict.GRAY_NEUTRAL -> Triple("#64748B", "#F8FAFC", "#CBD5E1")
        }
    }

    private fun formatCurrency(value: Double): String =
        String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value)

    private fun circleBg(fillHex: String, strokeHex: String, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fillHex))
            setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }
    }

    private fun roundedBg(hexColor: String, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun roundedCardBg(bgHex: String, strokeHex: String, radiusDp: Float, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(Color.parseColor(bgHex))
            setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
