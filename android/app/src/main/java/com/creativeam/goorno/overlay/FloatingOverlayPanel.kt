package com.creativeam.goorno.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.model.DriverFinancialConfig
import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.OverlayDisplayStyle
import com.creativeam.goorno.model.PassengerRatingVerdict
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.TrafficLightVerdict
import com.creativeam.goorno.util.StreetViewLauncher
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Painel Flutuante Nativo v3.3.25 — Livremente Arrastável na Tela + Banner Pro com Street View + Widget Pirulito.
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
    private var displayStyle: OverlayDisplayStyle = OverlayDisplayStyle.BANNER_PRO_STREETVIEW
    private var overlayBubblePosition: String = "BELOW"
    private var overlayHorizontalPosition: String = "CENTER"
    private var showFloatingShortcutBubble: Boolean = true
    private var showProfitOnOverlay: Boolean = false
    private var showCostOnOverlay: Boolean = false
    private var remainingSeconds: Int = 7
    private var isMonitoringEnabled: Boolean = true
    private var activeTimerLabel: TextView? = null
    private var lastKnownConfig: DriverFinancialConfig? = null

    // Persistent Floating Shortcut Bubble (Botãozinho Flutuante na Borda)
    private var shortcutBubbleContainer: View? = null
    private var shortcutBubbleParams: WindowManager.LayoutParams? = null
    private var isShortcutBubbleAdded: Boolean = false

    // Modal Flutuante de Atalhos Rápidos (Quick Settings Hub)
    private var quickModalContainer: View? = null
    private var quickModalParams: WindowManager.LayoutParams? = null
    private var isQuickModalAdded: Boolean = false

    var onUserRequestedDisableCopilot: (() -> Unit)? = null
    var onOfferCardClosed: (() -> Unit)? = null
    var onUserRequestedHideShortcutBubble: (() -> Unit)? = null
    var onUserUpdatedSemaforoQuickly: ((minKm: Double, minHr: Double, minRating: Double, maxPickupKm: Double) -> Unit)? = null

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
        mainHandler.post {
            updateShortcutBubbleVisibility()
        }
    }

    fun isCopilotMonitoringEnabled(): Boolean = isMonitoringEnabled

    fun isCardCurrentlyVisible(): Boolean = isAdded || isTransitioningBetweenOffers

    fun getActiveCardFingerprint(): String = activeCardFingerprint

    fun applyConfig(config: DriverFinancialConfig) {
        mainHandler.post {
            val previousHorizontalPos = overlayHorizontalPosition
            val previousBubblePos = overlayBubblePosition
            lastKnownConfig = config
            currentOpacityPercent = config.overlayOpacityPercent.coerceIn(25, 100)
            displayDurationSec = config.overlayAutoMinimizeSec.coerceIn(3, 45)
            displayStyle = OverlayDisplayStyle.fromName(config.overlayDisplayStyle)
            overlayBubblePosition = config.overlayBubblePosition
            overlayHorizontalPosition = config.overlayHorizontalPosition
            showFloatingShortcutBubble = config.showFloatingShortcutBubble
            showProfitOnOverlay = config.showProfitOnOverlay
            showCostOnOverlay = config.showCostOnOverlay
            isMonitoringEnabled = config.copilotMonitoringEnabled
            rootContainer?.alpha = currentOpacityPercent / 100f

            // Se o usuário mudou o alinhamento horizontal (Esquerda, Centro ou Direita)
            // ou a posição do balão (Acima ou Abaixo), reposiciona imediatamente a janela na tela!
            if (overlayHorizontalPosition == "CENTER" && previousHorizontalPos != "CENTER") {
                posPrefs.edit().remove("saved_x").apply()
            }
            if (previousHorizontalPos != overlayHorizontalPosition || previousBubblePos != overlayBubblePosition) {
                posPrefs.edit().remove("saved_x").remove("saved_y").apply()
                val (newGravity, newX) = when (overlayHorizontalPosition) {
                    "LEFT" -> Pair(Gravity.TOP or Gravity.START, dp(10))
                    "CENTER" -> Pair(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0)
                    else -> Pair(Gravity.TOP or Gravity.END, dp(10))
                }
                val newY = if (overlayBubblePosition == "ABOVE") dp(50) else dp(110)
                layoutParams?.let { lp ->
                    lp.gravity = newGravity
                    lp.x = newX
                    lp.y = newY
                    if (isAdded && rootContainer != null) {
                        try {
                            windowManager.updateViewLayout(rootContainer, lp)
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            if (!isMonitoringEnabled) {
                hidePanel()
            }
            updateShortcutBubbleVisibility()
        }
    }

    /**
     * Volta a janela flutuante para a posição padrão configurada (Esquerda, Centro ou Direita).
     */
    fun resetSavedPosition() {
        posPrefs.edit().clear().apply()
        mainHandler.post {
            layoutParams?.let { lp ->
                val (defaultGravity, defaultX) = when (overlayHorizontalPosition) {
                    "LEFT" -> Pair(Gravity.TOP or Gravity.START, dp(10))
                    "CENTER" -> Pair(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0)
                    else -> Pair(Gravity.TOP or Gravity.END, dp(10))
                }
                val defaultY = if (overlayBubblePosition == "ABOVE") dp(50) else dp(110)
                lp.gravity = defaultGravity
                lp.x = defaultX
                lp.y = defaultY
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
            recentlyDismissedOfferTimestamps.entries.removeAll { now - it.value > 60_000L }

            // Proteção Anti-Reabertura: se esta oferta já foi fechada/expirada recentemente,
            // ignora novas leituras da acessibilidade por 18s para a janela não ficar reabrindo em loop
            val lastDismissed = recentlyDismissedOfferTimestamps[newFingerprint]
            if (lastDismissed != null && (now - lastDismissed) < 18_000L && results.none { it.offer.isSimulated }) {
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

        // Oculta o botão flutuante de atalho enquanto um card de oferta estiver na tela (evita sobreposição)
        shortcutBubbleContainer?.visibility = View.GONE

        rootContainer?.alpha = currentOpacityPercent / 100f
        contentWrapper?.removeAllViews()

        remainingSeconds = displayDurationSec.coerceIn(3, 45)

        val builtView = when (displayStyle) {
            OverlayDisplayStyle.BANNER_PRO_STREETVIEW -> buildBannerProStreetView(currentEval, oneBasedIndex, totalInQueue)
            OverlayDisplayStyle.PIRULITO_SIDEBAR -> buildPirulitoSidebarView(currentEval, oneBasedIndex, totalInQueue)
            OverlayDisplayStyle.MINI_BALL -> buildMiniBallView(currentEval, oneBasedIndex, totalInQueue)
            OverlayDisplayStyle.CLASSIC_LIGHT -> if (lastKnownConfig?.overlayTheme == "DARK") buildClassicDarkCardView(currentEval, oneBasedIndex, totalInQueue) else buildClassicLightCardView(currentEval, oneBasedIndex, totalInQueue)
        }

        contentWrapper?.addView(builtView)

        activeTimerLabel?.text = "${remainingSeconds}s"
        mainHandler.removeCallbacks(countdownRunnable)
        mainHandler.postDelayed(countdownRunnable, 1000L)
    }

    private fun advanceToNextOfferOrFinish() {
        mainHandler.removeCallbacks(countdownRunnable)
        if (activeCardFingerprint.isNotBlank()) {
            recentlyDismissedOfferTimestamps[activeCardFingerprint] = System.currentTimeMillis()
        }
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
        if (activeCardFingerprint.isNotBlank()) {
            recentlyDismissedOfferTimestamps[activeCardFingerprint] = System.currentTimeMillis()
        }
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
            } catch (_: Throwable) {
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
            reconcileShortcutBubbleState()
        }
    }

    fun clearRecentlyDismissed() {
        mainHandler.post {
            recentlyDismissedOfferTimestamps.clear()
        }
    }

    private fun attachWindowIfNeeded() {
        if (!isAdded && rootContainer != null && layoutParams != null) {
            try {
                windowManager.addView(rootContainer, layoutParams)
                isAdded = true
                RuntimeDiagnosticsBus.setOverlayVisible(true)
            } catch (_: Throwable) {
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
    // FORMATO 1: BARRA SEMÁFORO COMPACTA (Todas as informações sem cortes)
    // =========================================================================


    // =========================================================================
    // FORMATO: NOVO CARD COMPACTO 3 PILARES (SUPER LEGÍVEL - NÚMEROS GRANDES)
    // - Sem valor em dinheiro grande no topo (super enxuto em altura)
    // - 3 Pilares com NÚMEROS GRANDES E PESADOS (25sp Bold), fáceis de ler ao dirigir
    // - Barrinhas verticais nítidas, linha inferior com tempo e distância
    // - Janelinhas de avisos (Risco, etc.) abrem separadas abaixo.
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildMiniBallView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val grossKm = result.grossPerKm ?: 0.0
        val grossHr = result.grossPerHour ?: 0.0
        val totalKm = result.totalConsideredDistanceKm ?: 0.0
        val totalMin = result.totalConsideredDurationMin ?: 0

        val targetKm = lastKnownConfig?.targetGrossPerKm ?: 2.50
        val minKm = lastKnownConfig?.minGrossPerKm ?: 1.80
        val targetHr = lastKnownConfig?.targetGrossPerHour ?: 50.00
        val minHr = lastKnownConfig?.minGrossPerHour ?: 35.00

        val isDark = lastKnownConfig?.overlayTheme != "LIGHT"
        val cardBgHex = "#131418"
        val primaryTextHex = "#FFFFFF"
        val labelTextHex = "#94A3B8"
        val subTextHex = "#CBD5E1"

        val kmBarColor = when {
            grossKm >= targetKm -> "#22C55E"
            grossKm >= minKm -> "#F59E0B"
            else -> "#EF4444"
        }
        val hrBarColor = when {
            grossHr >= targetHr -> "#22C55E"
            grossHr >= minHr -> "#F59E0B"
            else -> "#EF4444"
        }
        val ratingBarColor = when (result.passengerRatingVerdict) {
            PassengerRatingVerdict.GOOD -> "#22C55E"
            PassengerRatingVerdict.MEDIUM -> "#F59E0B"
            PassengerRatingVerdict.BAD -> "#EF4444"
            else -> "#94A3B8"
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Card Container Horizontal Enxuto (Borda sutil 1dp, sem distorção)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = roundedCardBg(cardBgHex, "#27272A", 16f, 1)
            layoutParams = LinearLayout.LayoutParams(dp(325), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
        }

        // Linha 1: Os 3 Pilares em Colunas com NÚMEROS GRANDES (25sp) E BARRINHAS NÍTIDAS
        val pillarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun makePillar(label: String, value: String, barColorHex: String): View {
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor(labelTextHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val valueRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            }
            val bar = View(context).apply {
                background = roundedBg(barColorHex, 2f)
                layoutParams = LinearLayout.LayoutParams(dp(5), dp(24)).apply {
                    marginEnd = dp(5)
                }
            }
            val tvVal = TextView(context).apply {
                text = value
                setTextColor(Color.parseColor(primaryTextHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            valueRow.addView(bar)
            valueRow.addView(tvVal)
            col.addView(tvLabel)
            col.addView(valueRow)
            return col
        }

        val kmStr = if (grossKm > 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", grossKm) else "--"
        val hrStr = if (grossHr > 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", grossHr) else "--"
        val ratStr = if (result.passengerRating != null) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating) else "--"

        pillarsRow.addView(makePillar("R$/Km", kmStr, kmBarColor))
        pillarsRow.addView(makePillar("R$/Hora", hrStr, hrBarColor))
        pillarsRow.addView(makePillar("Nota", ratStr, ratingBarColor))
        card.addView(pillarsRow)

        // Linha 2: Badge do App + Tempo • Distância + Timer + Fechar
        val subRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val is99 = offer.sourceApp == SourceApp.NINETY_NINE_DRIVER
        val isUber = offer.sourceApp == SourceApp.UBER_DRIVER

        val appBadge = TextView(context).apply {
            when {
                is99 -> {
                    text = "99"
                    setTextColor(Color.parseColor("#000000"))
                    background = roundedBg("#FACC15", 3f)
                }
                isUber -> {
                    text = "Uber"
                    setTextColor(Color.parseColor("#000000"))
                    background = roundedBg("#FFFFFF", 3f)
                }
                offer.isSimulated -> {
                    text = "SIM"
                    setTextColor(Color.parseColor("#FFFFFF"))
                    background = roundedBg("#4B5563", 3f)
                }
                else -> {
                    text = offer.sourceApp.displayName.take(5)
                    setTextColor(Color.parseColor("#FFFFFF"))
                    background = roundedBg("#4B5563", 3f)
                }
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        subRow.addView(appBadge)

        val distStr = if (totalKm > 0.0) {
            if (totalKm % 1.0 == 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f km", totalKm)
            else String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm)
        } else "-- km"
        val minStr = if (totalMin > 0) "${totalMin} min" else "-- min"

        val tvSub = TextView(context).apply {
            text = "— $minStr • $distStr —"
            setTextColor(Color.parseColor(subTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        subRow.addView(tvSub)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        subRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = roundedBg("#262930", 4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        subRow.addView(btnClose)

        card.addView(subRow)
        rootLayout.addView(card)

        // Janelinhas de Alertas Acopladas (Separadas e sem encavalar)
        buildAuxiliaryAlertPill(result, isDark)?.let { auxPill ->
            rootLayout.addView(auxPill)
        }

        return rootLayout
    }

    private fun buildBannerProStreetView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val grossKm = result.grossPerKm ?: 0.0
        val grossHr = result.grossPerHour ?: 0.0
        val totalKm = result.totalConsideredDistanceKm ?: 0.0
        val totalMin = result.totalConsideredDurationMin ?: 0
        val price = offer.offeredPrice ?: 0.0

        val (verdictBannerBg, verdictBannerText, verdictBorderColor) = when (result.verdict) {
            TrafficLightVerdict.RED -> Triple("#DC2626", "✕ NÃO COMPENSA", "#FF1744")
            TrafficLightVerdict.GREEN -> Triple("#16A34A", "✓ COMPENSA", "#00E676")
            TrafficLightVerdict.YELLOW -> Triple("#D97706", "⚡ AVALIAR", "#FFB300")
            TrafficLightVerdict.GRAY_NEUTRAL -> Triple("#475569", "⚪ INDEFINIDO", "#94A3B8")
        }

        val isDark = lastKnownConfig?.overlayTheme != "LIGHT"
        val cardBgHex = if (isDark) "#0A0B0E" else "#FFFFFF"
        val primaryTextHex = if (isDark) "#FFFFFF" else "#0F172A"
        val secondaryTextHex = if (isDark) "#F1F5F9" else "#1E293B"
        val metricsColorHex = if (isDark) "#FFFFFF" else "#0F172A"
        val timerBgHex = if (isDark) "#1E222A" else "#E2E8F0"

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Card Container com Borda Negritada e Vibrante do Veredito (4dp de espessura)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedCardBg(cardBgHex, verdictBorderColor, 14f, 4)
            layoutParams = LinearLayout.LayoutParams(dp(310), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // Área Superior do Conteúdo (com espaçamento interno de respiro)
        val contentArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Linha Superior: Distância e Tempo (substituindo CORRIDA TOCOU) + Temporizador + Botão Fechar ✕
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val distStr = if (totalKm > 0.0) {
            if (totalKm % 1.0 == 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f km", totalKm)
            else String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm)
        } else {
            "-- km"
        }
        val minStr = if (totalMin > 0) "${totalMin} min" else "-- min"
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""
        val headerLabel = "$seqTag$distStr · $minStr"

        val tvHeader = TextView(context).apply {
            text = headerLabel
            setTextColor(Color.parseColor(secondaryTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvHeader)

        // Temporizador Regressivo
        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor(secondaryTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = roundedBg(timerBgHex, 6f)
        }
        activeTimerLabel = tvTimer
        topRow.addView(tvTimer)

        // Botão Fechar ✕
        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor(secondaryTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(2), dp(2), dp(2))
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        topRow.addView(btnClose)

        contentArea.addView(topRow)

        // 2. Valor Gigante da Corrida (ex: R$ 9,00 ou R$ 18,40)
        val tvPrice = TextView(context).apply {
            text = formatCurrency(price)
            setTextColor(Color.parseColor(primaryTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f)
            try {
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            } catch (_: Throwable) {
                setTypeface(typeface, Typeface.BOLD)
            }
            setPadding(0, dp(2), 0, dp(1))
            setSingleLine(true)
        }
        contentArea.addView(tvPrice)

        // 3. Linha de Métricas: R$/km · R$/h (com valor por hora conforme solicitado)
        val grossKmStr = if (grossKm > 0.0) {
            "${formatCurrency(grossKm)}/km"
        } else {
            "--/km"
        }
        val grossHrStr = if (grossHr > 0.0) {
            String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/h", grossHr)
        } else {
            "--/h"
        }
        val ratingTag = if (result.passengerRating != null) {
            " · ★" + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating)
        } else ""

        val tvMetrics = TextView(context).apply {
            text = "$grossKmStr · $grossHrStr$ratingTag"
            setTextColor(Color.parseColor(metricsColorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
        }
        contentArea.addView(tvMetrics)



        card.addView(contentArea)

        // 4. Barra Inferior de Veredito Sólido (✕ NÃO COMPENSA / ✓ COMPENSA / ⚡ AVALIAR)
        val bannerBottom = TextView(context).apply {
            text = verdictBannerText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBottomBg(verdictBannerBg, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        }
        card.addView(bannerBottom)

        rootLayout.addView(card)
        buildAuxiliaryAlertPill(result, isDark)?.let { auxPill ->
            rootLayout.addView(auxPill)
        }
        return rootLayout
    }

    // =========================================================================
    // FORMATO: CARD 3 PILARES (R$/KM, R$/HORA E NOTA COM BARRAS VERTICAIS)
    // IDÊNTICO ÀS IMAGENS FORNECIDAS PELO USUÁRIO (VERDE/AMARELO/VERMELHO)
    // SUPORTA TEMA CLARO (FUNDO BRANCO) OU TEMA ESCURO (FUNDO PRETO)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildPirulitoSidebarView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val grossKm = result.grossPerKm ?: 0.0
        val grossHr = result.grossPerHour ?: 0.0
        val totalKm = result.totalConsideredDistanceKm ?: 0.0
        val totalMin = result.totalConsideredDurationMin ?: 0
        val price = offer.offeredPrice ?: 0.0

        val targetKm = lastKnownConfig?.targetGrossPerKm ?: 2.50
        val minKm = lastKnownConfig?.minGrossPerKm ?: 1.80
        val targetHr = lastKnownConfig?.targetGrossPerHour ?: 50.00
        val minHr = lastKnownConfig?.minGrossPerHour ?: 35.00

        val isDark = lastKnownConfig?.overlayTheme != "LIGHT"

        val verdictBorderColor = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "#00E676"  // Verde Neon ultra-visível
            TrafficLightVerdict.YELLOW -> "#FFB300" // Âmbar Ouro puro e vibrante
            TrafficLightVerdict.RED -> "#FF1744"    // Vermelho vivo e marcante
            TrafficLightVerdict.GRAY_NEUTRAL -> "#94A3B8"
        }

        val cardBg = if (isDark) "#0A0B0E" else "#FFFFFF"
        val primaryTextColor = if (isDark) Color.WHITE else Color.parseColor("#0F172A")
        val labelTextColor = if (isDark) Color.parseColor("#F1F5F9") else Color.parseColor("#1E293B")
        val secondaryTextColor = if (isDark) Color.parseColor("#E2E8F0") else Color.parseColor("#334155")

        val kmBarColor = when {
            grossKm >= targetKm -> "#00E676"
            grossKm >= minKm -> "#FFB300"
            else -> "#FF1744"
        }
        val hrBarColor = when {
            grossHr >= targetHr -> "#00E676"
            grossHr >= minHr -> "#FFB300"
            else -> "#FF1744"
        }
        val ratingColorHex = when (result.passengerRatingVerdict) {
            PassengerRatingVerdict.GOOD -> "#00E676"
            PassengerRatingVerdict.MEDIUM -> "#FFB300"
            PassengerRatingVerdict.BAD -> "#FF1744"
            else -> if (isDark) "#FFFFFF" else "#0F172A"
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Card Container com Borda Negritada (4dp de espessura) e Fundo 100% Sólido
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedCardBg(cardBg, verdictBorderColor, 18f, 4)
            layoutParams = LinearLayout.LayoutParams(dp(310), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // 1. Linha Superior: Resumo (R$ 12,70 · 11 min · 5,6 km) + Temporizador + Fechar
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

        val distStr = if (totalKm > 0.0) {
            if (totalKm % 1.0 == 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f km", totalKm)
            else String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm)
        } else {
            "-- km"
        }
        val minStr = if (totalMin > 0) "${totalMin} min" else "-- min"
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""
        val summaryText = "$seqTag${formatCurrency(price)} · $minStr · $distStr"

        val tvSummary = TextView(context).apply {
            text = summaryText
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvSummary)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), 0)
        }
        activeTimerLabel = tvTimer
        topRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(2), 0)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        topRow.addView(btnClose)

        card.addView(topRow)

        // 2. Linha dos 3 Pilares: [R$/Km] | [R$/Hora] | [Nota]
        val pillarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Coluna 1: R$/Km
        val col1 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLabelKm = TextView(context).apply {
            text = "R$/Km"
            setTextColor(labelTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val tvValKm = TextView(context).apply {
            text = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", grossKm)
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        col1.addView(tvLabelKm)
        col1.addView(tvValKm)
        pillarsRow.addView(col1)

        // Barra Separadora 1 (Colorida e mais espessa)
        val bar1 = View(context).apply {
            background = roundedBg(kmBarColor, 2f)
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(38)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        pillarsRow.addView(bar1)

        // Coluna 2: R$/Hora
        val col2 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLabelHr = TextView(context).apply {
            text = "R$/Hora"
            setTextColor(labelTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val tvValHr = TextView(context).apply {
            text = String.format(Locale.forLanguageTag("pt-BR"), "%.0f", grossHr)
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        col2.addView(tvLabelHr)
        col2.addView(tvValHr)
        pillarsRow.addView(col2)

        // Barra Separadora 2 (Colorida e mais espessa)
        val bar2 = View(context).apply {
            background = roundedBg(hrBarColor, 2f)
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(38)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        pillarsRow.addView(bar2)

        // Coluna 3: Nota do Passageiro (Valor colorido com a cor da avaliação)
        val col3 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLabelRating = TextView(context).apply {
            text = "Nota"
            setTextColor(labelTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val ratingText = if (result.passengerRating != null) {
            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating)
        } else {
            "--"
        }
        val tvValRating = TextView(context).apply {
            text = ratingText
            setTextColor(Color.parseColor(ratingColorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        col3.addView(tvLabelRating)
        col3.addView(tvValRating)
        pillarsRow.addView(col3)

        card.addView(pillarsRow)

        rootLayout.addView(card)

        // Janelinha Auxiliar de Alertas Acoplada (Abre, fecha e arrasta sincronizada com o card principal)
        buildAuxiliaryAlertPill(result, isDark)?.let { auxPill ->
            rootLayout.addView(auxPill)
        }

        return rootLayout
    }
    // =========================================================================
    // JANELINHA AUXILIAR DE ALERTAS ACOPLADA (DESIGN IMAGENS 4 E 5)
    // Acoplada logo abaixo do card principal no rootLayout.
    // Abre, fecha e move sincronizada sem poluir ou desconfigurar as proporções do card.
    // =========================================================================
    private data class AlertPillItem(
        val icon: String,
        val label: String,
        val detail: String,
        val labelColor: Int,
        val detailColor: Int
    )

    private fun buildAuxiliaryAlertPill(
        result: FinancialEvaluationResult,
        isDark: Boolean
    ): View? {
        val alerts = mutableListOf<AlertPillItem>()

        // 1. Área de Risco (Alerta prioritário de segurança)
        if (result.hasRiskAlert) {
            val riskText = result.riskAlertBadge.orEmpty().ifBlank { "Região de Atenção" }
            alerts.add(
                AlertPillItem(
                    icon = "⚠️",
                    label = "ÁREA DE RISCO:",
                    detail = riskText,
                    labelColor = Color.parseColor(if (isDark) "#F87171" else "#DC2626"),
                    detailColor = Color.parseColor(if (isDark) "#FECACA" else "#991B1B")
                )
            )
        }

        // 2. Supermercado (Atacadista / Compras) - Idêntico à Imagem 4
        if (result.isSupermarket) {
            val sName = result.supermarketName ?: "Atacadista / Compras"
            alerts.add(
                AlertPillItem(
                    icon = "🛒",
                    label = "SUPERMERCADO:",
                    detail = sName,
                    labelColor = Color.parseColor(if (isDark) "#F6C764" else "#B45309"),
                    detailColor = Color.parseColor(if (isDark) "#F6E3B4" else "#78350F")
                )
            )
        }

        // 3. Paradas Intermediárias - Idêntico à Imagem 5
        if (!result.stopsDescription.isNullOrBlank()) {
            alerts.add(
                AlertPillItem(
                    icon = "🛑",
                    label = "PARADAS:",
                    detail = result.stopsDescription,
                    labelColor = Color.parseColor(if (isDark) "#7DD3FC" else "#0284C7"),
                    detailColor = Color.parseColor(if (isDark) "#BAE6FD" else "#0369A1")
                )
            )
        }

        // 4. Chamado Repetido (Oferta reemitida pelo app)
        if (result.isRepeatedOffer) {
            alerts.add(
                AlertPillItem(
                    icon = "🔁",
                    label = "CHAMADO REPETIDO:",
                    detail = "Oferta reemitida",
                    labelColor = Color.parseColor(if (isDark) "#FB923C" else "#EA580C"),
                    detailColor = Color.parseColor(if (isDark) "#FED7AA" else "#7C2D12")
                )
            )
        }

        // 5. Embarque Longe (Distância até o passageiro > 2 km)
        if (result.isDistantPickup) {
            val kmText = result.offer.pickupDistanceKm?.let {
                String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
            } ?: ""
            val detailText = if (kmText.isNotBlank()) "$kmText até passageiro" else "Passageiro distante"
            alerts.add(
                AlertPillItem(
                    icon = "⚠️",
                    label = "EMB. LONGE:",
                    detail = detailText,
                    labelColor = Color.parseColor(if (isDark) "#FDE047" else "#CA8A04"),
                    detailColor = Color.parseColor(if (isDark) "#FEF08A" else "#713F12")
                )
            )
        }

        // 6. Região Favorita
        if (result.isFavoriteZone && !result.hasRiskAlert) {
            val favText = result.favoriteZoneBadge.orEmpty().ifBlank { "Destino Preferido" }
            alerts.add(
                AlertPillItem(
                    icon = "⭐",
                    label = "REGIÃO FAVORITA:",
                    detail = favText,
                    labelColor = Color.parseColor(if (isDark) "#4ADE80" else "#16A34A"),
                    detailColor = Color.parseColor(if (isDark) "#BBF7D0" else "#14532D")
                )
            )
        }

        // 7. Viagem Longa
        if (result.isLongTrip && alerts.size < 2) {
            val timeText = if ((result.totalConsideredDurationMin ?: 0) >= 60) {
                val h = (result.totalConsideredDurationMin ?: 0) / 60
                val m = (result.totalConsideredDurationMin ?: 0) % 60
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else {
                "${result.totalConsideredDurationMin ?: 0} min"
            }
            alerts.add(
                AlertPillItem(
                    icon = "⏱️",
                    label = "VIAGEM LONGA:",
                    detail = "$timeText de viagem",
                    labelColor = Color.parseColor(if (isDark) "#818CF8" else "#4F46E5"),
                    detailColor = Color.parseColor(if (isDark) "#C7D2FE" else "#312E81")
                )
            )
        }

        // 8. Fallback operacional caso haja outro aviso não mapeado
        if (alerts.isEmpty() && !result.operationalBadge.isNullOrBlank()) {
            alerts.add(
                AlertPillItem(
                    icon = "⚡",
                    label = "AVISO:",
                    detail = result.operationalBadge,
                    labelColor = Color.parseColor(if (isDark) "#F59E0B" else "#D97706"),
                    detailColor = Color.parseColor(if (isDark) "#FDE68A" else "#78350F")
                )
            )
        }

        if (alerts.isEmpty()) return null

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }

        val pillBgHex = if (isDark) "#061510" else "#F1F5F9"
        val pillStrokeHex = if (isDark) "#174334" else "#CBD5E1"

        alerts.take(2).forEachIndexed { i, alert ->
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedCardBg(pillBgHex, pillStrokeHex, 16f, 1)
                setPadding(dp(12), dp(5), dp(12), dp(5))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (i > 0) topMargin = dp(4)
                }
            }

            val tvIcon = TextView(context).apply {
                text = alert.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, 0, dp(6), 0)
            }
            pill.addView(tvIcon)

            val tvLabel = TextView(context).apply {
                text = "${alert.label} "
                setTextColor(alert.labelColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setSingleLine(true)
            }
            pill.addView(tvLabel)

            val tvDetail = TextView(context).apply {
                text = alert.detail
                setTextColor(alert.detailColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(210)
            }
            pill.addView(tvDetail)

            container.addView(pill)
        }

        return container
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun buildModernSpeechBubbleCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        return buildBannerProStreetView(result, index, totalCount)
    }

    private fun legacyBuildModernSpeechBubbleCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val isBubbleAbove = overlayBubblePosition != "BELOW"

        // Cores e texto de veredito de alto contraste idênticos ao padrão do mercado (Imagem 1)
        val (verdictText, verdictBgHex, verdictTextColor) = when (result.verdict) {
            TrafficLightVerdict.GREEN -> Triple("✓  ACEITAR", "#00C853", Color.parseColor("#052E16"))
            TrafficLightVerdict.YELLOW -> Triple("●  AVALIAR", "#FACC15", Color.parseColor("#422006"))
            TrafficLightVerdict.RED -> Triple("✕  RECUSAR", "#EF4444", Color.WHITE)
            TrafficLightVerdict.GRAY_NEUTRAL -> Triple("●  AVALIAR", "#64748B", Color.WHITE)
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // 1. PÍLULA DE VEREDITO FLUTUANTE (ACEITAR / AVALIAR / RECUSAR)
        val verdictPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(7), dp(22), dp(7))
            background = roundedBg(verdictBgHex, 22f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (isBubbleAbove) {
                    bottomMargin = dp(6)
                } else {
                    topMargin = dp(6)
                }
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        val tvVerdict = TextView(context).apply {
            text = verdictText
            setTextColor(verdictTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        verdictPill.addView(tvVerdict)

        // 2. CARD ESCURO (DESIGN MODERNO, TRANSLÚCIDO E COM BORDA DA COR DO VEREDITO - IMAGEM 2)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedCardBg("#111F1A", verdictBgHex, 18f, 2)
            layoutParams = LinearLayout.LayoutParams(dp(355), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // Barra decorativa luminosa no topo do card (idêntico à Imagem 2)
        val topAccent = View(context).apply {
            background = roundedBg(verdictBgHex, 2f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            ).apply {
                bottomMargin = dp(8)
            }
        }
        card.addView(topAccent)

        // Subtítulo do Topo em Linha Única: "Uber - R$ 14,01 · 17 min · 6,3 km          7s" (idêntico à Imagem 2)
        val distStr = result.totalConsideredDistanceKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
        } ?: "-- km"
        val minStr = result.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"
        val priceStr = offer.offeredPrice?.let { " - " + formatCurrency(it) } ?: ""
        val appName = when {
            offer.isSimulated -> "Simulação"
            offer.sourceApp == SourceApp.UBER_DRIVER -> "Uber"
            offer.sourceApp == SourceApp.NINETY_NINE_DRIVER -> "99"
            else -> offer.sourceApp.displayName
        }
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""

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
            text = "$seqTag$appName$priceStr · $minStr · $distStr"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvSubtitle)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        activeTimerLabel = tvTimer
        topRow.addView(tvTimer)
        card.addView(topRow)

        // 3. LINHA DOS PILARES COM BARRAS VERTICAIS COLORIDAS E NÚMEROS GIGANTES (28sp)
        val pillarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val totalPillars = 3 + (if (showProfitOnOverlay) 1 else 0) + (if (showCostOnOverlay) 1 else 0)
        val valueTextSize = if (totalPillars > 3) 20f else 28f
        val barHeight = if (totalPillars > 3) dp(20) else dp(26)
        val barWidth = if (totalPillars > 3) dp(3) else dp(4)

        fun addPillar(label: String, valueText: String, barColorHex: String) {
            val pillar = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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
                layoutParams = LinearLayout.LayoutParams(barWidth, barHeight).apply {
                    marginEnd = dp(5)
                }
            }
            valueRow.addView(verticalBar)

            val tvValue = TextView(context).apply {
                text = valueText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, valueTextSize)
                setTypeface(typeface, Typeface.BOLD)
                setSingleLine(true)
                includeFontPadding = false
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

        // 3. Pilar: Nota do Passageiro
        val ratingVal = result.passengerRating?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", it)
        } ?: "--"
        val ratingBarColor = when (result.passengerRatingVerdict) {
            PassengerRatingVerdict.GOOD -> "#00C853"
            PassengerRatingVerdict.MEDIUM -> "#FACC15"
            PassengerRatingVerdict.BAD -> "#EF4444"
            PassengerRatingVerdict.UNKNOWN -> "#64748B"
        }
        addPillar("Nota", ratingVal, ratingBarColor)

        // 4. Pilar Opcional: Lucro (apenas se o motorista ativar nas configurações)
        if (showProfitOnOverlay) {
            val price = offer.offeredPrice ?: 0.0
            val netProfit = result.estimatedNetProfit
            val netPercentVal = if (price > 0.0 && netProfit != null) {
                val pct = (netProfit / price) * 100.0
                String.format(Locale.forLanguageTag("pt-BR"), "%.0f%%", pct)
            } else if (netProfit != null) {
                String.format(Locale.forLanguageTag("pt-BR"), "R$%.0f", netProfit)
            } else {
                "--"
            }
            val profitBarColor = when {
                netProfit == null -> "#64748B"
                netProfit > 0.0 -> "#00C853"
                else -> "#EF4444"
            }
            addPillar("Lucro", netPercentVal, profitBarColor)
        }

        // 5. Pilar Opcional: Custo (apenas se o motorista ativar nas configurações)
        if (showCostOnOverlay) {
            val totalCost = result.estimatedTotalCost
            val costVal = if (totalCost != null) {
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.1f", totalCost)
            } else {
                "--"
            }
            addPillar("Custo", costVal, "#94A3B8")
        }

        card.addView(pillarsRow)

        // Se houver alerta de Área de Risco:
        if (result.hasRiskAlert && !result.riskAlertBadge.isNullOrBlank()) {
            val tvRisk = TextView(context).apply {
                text = result.riskAlertBadge
                setTextColor(Color.parseColor("#FDE047"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBg("#450A0A", 6f)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }
            card.addView(tvRisk)
        }

        // Se houver destaque de Região Favorita:
        if (result.isFavoriteZone && !result.favoriteZoneBadge.isNullOrBlank()) {
            val tvFav = TextView(context).apply {
                text = "⭐ REGIÃO FAVORITA: ${result.favoriteZoneBadge}"
                setTextColor(Color.parseColor("#6EE7B7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBg("#064E3B", 6f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(if (result.hasRiskAlert) 4 else 8)
                }
            }
            card.addView(tvFav)
        }

        // Se o balão estiver ACIMA, adiciona a barra decorativa de destaque na BASE do card (conforme padrão)
        if (isBubbleAbove) {
            val bottomAccent = View(context).apply {
                background = roundedBg(verdictBgHex, 2f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(4)
                ).apply {
                    topMargin = dp(8)
                }
            }
            card.addView(bottomAccent)
        }

        if (isBubbleAbove) {
            rootLayout.addView(verdictPill)
            rootLayout.addView(card)
        } else {
            rootLayout.addView(card)
            rootLayout.addView(verdictPill)
        }
        return rootLayout
    }

    // =========================================================================
    // NOVO CARD CLÁSSICO DIVIDIDO (BLOCO DE COR SÓLIDO NA DIREITA - SÓ A COR)
    // - Lado Esquerdo: Dados da corrida limpos (R$/km em destaque, R$/hora, Nota ★, App + Distância)
    // - Lado Direito: Bloco vertical sólido na cor do semáforo (SEM TEXTO, SÓ A COR para visão reflexiva)
    // - Borda sutil 1dp, sem distorção, sem encavalamento de textos.
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildClassicSplitCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int,
        isDark: Boolean
    ): View {
        val offer = result.offer
        val grossKm = result.grossPerKm ?: 0.0
        val grossHr = result.grossPerHour ?: 0.0
        val totalKm = result.totalConsideredDistanceKm ?: 0.0
        val totalMin = result.totalConsideredDurationMin ?: 0

        val verdictColorHex = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "#22C55E"
            TrafficLightVerdict.YELLOW -> "#F59E0B"
            TrafficLightVerdict.RED -> "#EF4444"
            TrafficLightVerdict.GRAY_NEUTRAL -> "#64748B"
        }

        val cardBgHex = "#131418"
        val subTextHex = "#9CA3AF"

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Card Container Horizontal Integrado
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedCardBg(cardBgHex, "#27272A", 16f, 1)
            layoutParams = LinearLayout.LayoutParams(dp(325), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
        }

        // 1. SEÇÃO DA ESQUERDA (DADOS DA CORRIDA)
        val leftSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Linha 1: R$ 1,92 /km + [timer e fechar]
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val kmStr = if (grossKm > 0.0) String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", grossKm) else "R$ --"
        val tvKmVal = TextView(context).apply {
            text = kmStr
            setTextColor(Color.parseColor(verdictColorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(typeface, Typeface.BOLD)
        }
        row1.addView(tvKmVal)

        val tvKmUnit = TextView(context).apply {
            text = " /km"
            setTextColor(Color.parseColor(subTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }
        row1.addView(tvKmUnit)

        val spacer1 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        row1.addView(spacer1)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.parseColor("#6B7280"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        row1.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = roundedBg("#262930", 4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(5)
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        row1.addView(btnClose)

        leftSection.addView(row1)

        // Linha 2: R$ 33,33/h       ★ 4,92
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }

        val hrStr = if (grossHr > 0.0) String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/h", grossHr) else "R$ --/h"
        val tvHr = TextView(context).apply {
            text = hrStr
            setTextColor(Color.parseColor(verdictColorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        row2.addView(tvHr)

        val ratVal = result.passengerRating
        if (ratVal != null && ratVal > 0.0) {
            val tvRatStar = TextView(context).apply {
                text = "★"
                setTextColor(Color.parseColor("#F59E0B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), 0, dp(3), 0)
            }
            row2.addView(tvRatStar)

            val tvRatVal = TextView(context).apply {
                text = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratVal)
                setTextColor(Color.parseColor(verdictColorHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTypeface(typeface, Typeface.BOLD)
            }
            row2.addView(tvRatVal)
        }
        leftSection.addView(row2)

        // Linha 3: [99]  18 min - 5,2 km
        val row3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }

        val is99 = offer.sourceApp == SourceApp.NINETY_NINE_DRIVER
        val isUber = offer.sourceApp == SourceApp.UBER_DRIVER

        val appBadge = TextView(context).apply {
            when {
                is99 -> {
                    text = "99"
                    setTextColor(Color.parseColor("#000000"))
                    background = roundedBg("#FACC15", 3f)
                }
                isUber -> {
                    text = "Uber"
                    setTextColor(Color.parseColor("#000000"))
                    background = roundedBg("#FFFFFF", 3f)
                }
                offer.isSimulated -> {
                    text = "SIM"
                    setTextColor(Color.parseColor("#FFFFFF"))
                    background = roundedBg("#4B5563", 3f)
                }
                else -> {
                    text = offer.sourceApp.displayName.take(5)
                    setTextColor(Color.parseColor("#FFFFFF"))
                    background = roundedBg("#4B5563", 3f)
                }
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        row3.addView(appBadge)

        val distStr = if (totalKm > 0.0) {
            if (totalKm % 1.0 == 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f km", totalKm)
            else String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm)
        } else "-- km"
        val minStr = if (totalMin > 0) "${totalMin} min" else "-- min"

        val tvTrip = TextView(context).apply {
            text = "$minStr - $distStr"
            setTextColor(Color.parseColor(subTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
            }
        }
        row3.addView(tvTrip)

        leftSection.addView(row3)
        card.addView(leftSection)

        // 2. BLOCO DA DIREITA: BLOCO SÓLIDO DE COR (SEM TEXTO, SÓ A COR PARA VISÃO REFLEXIVA)
        val rightColorBlock = View(context).apply {
            background = roundedBg(verdictColorHex, 10f)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(66)).apply {
                marginEnd = dp(8)
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }
        card.addView(rightColorBlock)

        rootLayout.addView(card)

        // Janelinhas de Alertas Acopladas (Separadas e sem encavalar)
        buildAuxiliaryAlertPill(result, isDark)?.let { auxPill ->
            rootLayout.addView(auxPill)
        }

        return rootLayout
    }

    private fun buildClassicDarkCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View = buildClassicSplitCardView(result, index, totalCount, true)

    private fun buildClassicLightCardView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View = buildClassicSplitCardView(result, index, totalCount, false)

    // =========================================================================
    // BOTÃO FLUTUANTE DE ATALHO NA TELA (DRAGGABLE SHORTCUT BUBBLE)
    // Permanece na borda da tela e abre o Hub de Atalhos Rápidos ao ser tocado
    // =========================================================================
    fun updateShortcutBubbleVisibility() {
        mainHandler.post {
            reconcileShortcutBubbleState()
        }
    }

    fun reconcileShortcutBubbleState() {
        val shouldShow = hasOverlayPermission() && isMonitoringEnabled && showFloatingShortcutBubble && !isCardCurrentlyVisible()
        if (shouldShow) {
            if (!isShortcutBubbleAdded || shortcutBubbleContainer == null) {
                showShortcutBubble()
            } else {
                try {
                    shortcutBubbleContainer?.visibility = View.VISIBLE
                } catch (_: Throwable) {
                    hideShortcutBubble()
                    showShortcutBubble()
                }
            }
        } else {
            if (isCardCurrentlyVisible()) {
                shortcutBubbleContainer?.visibility = View.GONE
            } else {
                hideShortcutBubble()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showShortcutBubble() {
        if (isShortcutBubbleAdded && shortcutBubbleContainer != null) return
        if (!hasOverlayPermission()) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val savedX = posPrefs.getInt("shortcut_x", screenWidth - dp(56))
        val savedY = posPrefs.getInt("shortcut_y", dp(220))

        shortcutBubbleParams = WindowManager.LayoutParams(
            dp(50),
            dp(50),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedCardBg("#FFFFFF", "#10B981", 16f, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(8).toFloat()
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))

            val logoIv = android.widget.ImageView(context).apply {
                setImageResource(com.creativeam.goorno.R.drawable.logo_goorno)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            }
            addView(logoIv)

            setOnTouchListener(createShortcutBubbleTouchListener())
        }

        shortcutBubbleContainer = bubble
        try {
            windowManager.addView(bubble, shortcutBubbleParams)
            isShortcutBubbleAdded = true
        } catch (_: Throwable) {
        }
    }

    fun hideShortcutBubble() {
        if (isShortcutBubbleAdded && shortcutBubbleContainer != null) {
            try {
                windowManager.removeView(shortcutBubbleContainer)
            } catch (_: Throwable) {
            }
            isShortcutBubbleAdded = false
            shortcutBubbleContainer = null
        }
        dismissQuickSettingsModal()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createShortcutBubbleTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var wasDragged = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = shortcutBubbleParams ?: return false
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
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                            wasDragged = true
                            lp.x = initialX + dx
                            lp.y = (initialY + dy).coerceAtLeast(0)
                            if (isShortcutBubbleAdded && shortcutBubbleContainer != null) {
                                try {
                                    windowManager.updateViewLayout(shortcutBubbleContainer, lp)
                                } catch (_: Throwable) {
                                }
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (wasDragged) {
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            val snapX = if (lp.x + dp(25) < screenWidth / 2) dp(8) else screenWidth - dp(58)
                            lp.x = snapX
                            posPrefs.edit()
                                .putInt("shortcut_x", lp.x)
                                .putInt("shortcut_y", lp.y)
                                .apply()
                            if (isShortcutBubbleAdded && shortcutBubbleContainer != null) {
                                try {
                                    windowManager.updateViewLayout(shortcutBubbleContainer, lp)
                                } catch (_: Throwable) {
                                }
                            }
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            openQuickSettingsModal()
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    // =========================================================================
    // MODAL FLUTUANTE DE ATALHOS RÁPIDOS (QUICK SETTINGS HUB DIALOG)
    // =========================================================================
    fun openQuickSettingsModal() {
        if (isQuickModalAdded) {
            dismissQuickSettingsModal()
            return
        }
        if (!hasOverlayPermission()) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayWidth = context.resources.displayMetrics.widthPixels
        val modalWidth = (displayWidth - dp(32)).coerceAtMost(dp(340))

        quickModalParams = WindowManager.LayoutParams(
            modalWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val modalView = buildQuickSettingsModalView()
        quickModalContainer = modalView

        try {
            windowManager.addView(modalView, quickModalParams)
            isQuickModalAdded = true
        } catch (_: Throwable) {
        }
    }

    fun dismissQuickSettingsModal() {
        if (isQuickModalAdded && quickModalContainer != null) {
            try {
                windowManager.removeView(quickModalContainer)
            } catch (_: Throwable) {
            }
            isQuickModalAdded = false
            quickModalContainer = null
        }
    }

    private fun buildQuickSettingsModalView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedCardBg("#FFFFFF", "#CBD5E1", 20f, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(16).toFloat()
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            text = "GoOrNo • Atalhos Rápidos"
            setTextColor(Color.parseColor("#0F2F28"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvSub = TextView(context).apply {
            text = "● Monitorando Uber & 99"
            setTextColor(Color.parseColor("#059669"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
        }
        titleCol.addView(tvTitle)
        titleCol.addView(tvSub)
        topRow.addView(titleCol)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBg("#F1F5F9", 12f)
            setOnClickListener { dismissQuickSettingsModal() }
        }
        topRow.addView(btnClose)
        root.addView(topRow)

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        lateinit var showHubView: () -> Unit
        lateinit var showSemaforoAdjusterView: () -> Unit

        showHubView = {
            contentContainer.removeAllViews()

            val cardsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            }

            fun createActionCard(
                icon: String,
                title: String,
                sub: String,
                borderHex: String,
                bgHex: String,
                subTextColor: String,
                onClick: () -> Unit
            ): View {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    background = roundedCardBg(bgHex, borderHex, 14f, 1)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    setOnClickListener { onClick() }

                    val tvIcon = TextView(context).apply {
                        text = icon
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                        gravity = Gravity.CENTER
                    }
                    addView(tvIcon)

                    val tvT = TextView(context).apply {
                        text = title
                        setTextColor(Color.parseColor("#0F2F28"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(0, dp(3), 0, 0)
                    }
                    addView(tvT)

                    val tvS = TextView(context).apply {
                        text = sub
                        setTextColor(Color.parseColor(subTextColor))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                    }
                    addView(tvS)
                }
            }

            val kmStr = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", lastKnownConfig?.targetGrossPerKm ?: 2.0)
            cardsRow.addView(createActionCard("🚦", "Semáforo", kmStr, "#BBF7D0", "#F0FDF4", "#15803D") {
                showSemaforoAdjusterView()
            })
            cardsRow.addView(createActionCard("⏱️", "Metas", "Financeiro", "#BAE6FD", "#F0F9FF", "#0369A1") {
                launchAppScreen("FINANCIAL_TARGETS")
                dismissQuickSettingsModal()
            })
            cardsRow.addView(createActionCard("🛡️", "Segurança", "Áreas Risco", "#FECDD3", "#FFF1F2", "#BE123C") {
                launchAppScreen("SECURITY")
                dismissQuickSettingsModal()
            })
            contentContainer.addView(cardsRow)

            fun createListAction(icon: String, title: String, subtitle: String, onClick: () -> Unit): View {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = roundedCardBg("#F8FAFC", "#E2E8F0", 12f, 1)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    setOnClickListener { onClick() }

                    val tvI = TextView(context).apply {
                        text = icon
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setPadding(0, 0, dp(10), 0)
                    }
                    addView(tvI)

                    val textCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val tvT = TextView(context).apply {
                        text = title
                        setTextColor(Color.parseColor("#0F2F28"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        setTypeface(typeface, Typeface.BOLD)
                    }
                    val tvSub = TextView(context).apply {
                        text = subtitle
                        setTextColor(Color.parseColor("#64748B"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    }
                    textCol.addView(tvT)
                    textCol.addView(tvSub)
                    addView(textCol)

                    val tvArrow = TextView(context).apply {
                        text = "›"
                        setTextColor(Color.parseColor("#94A3B8"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        setTypeface(typeface, Typeface.BOLD)
                    }
                    addView(tvArrow)
                }
            }

            contentContainer.addView(createListAction("📋", "Histórico de Corridas", "Ver chamadas capturadas e mapas") {
                launchAppScreen("HISTORY")
                dismissQuickSettingsModal()
            })
            contentContainer.addView(createListAction("⚙️", "Abrir GoOrNo Completo", "Painel principal e configurações") {
                launchAppScreen("HOME")
                dismissQuickSettingsModal()
            })
            contentContainer.addView(createListAction("👁️", "Ocultar Botão Flutuante", "Pode reativar depois no menu Janela") {
                hideShortcutBubble()
                onUserRequestedHideShortcutBubble?.invoke()
                android.widget.Toast.makeText(
                    context,
                    "Botão ocultado. Para reativar, acesse GoOrNo > Formato da Janela.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            })
        }

        showSemaforoAdjusterView = {
            contentContainer.removeAllViews()

            val editorLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }

            val tvH = TextView(context).apply {
                text = "⚡ Ajuste Rápido do Semáforo"
                setTextColor(Color.parseColor("#0F2F28"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            }
            editorLayout.addView(tvH)

            var curKm = lastKnownConfig?.targetGrossPerKm ?: 2.0
            var curHr = lastKnownConfig?.targetGrossPerHour ?: 40.0
            var curRating = lastKnownConfig?.targetGoodPassengerRating ?: 4.85
            var curPickup = lastKnownConfig?.distantPickupKmThreshold ?: 2.5

            fun createStepperRow(label: String, initialValueText: String, onMinus: () -> String, onPlus: () -> String): View {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = roundedCardBg("#F8FAFC", "#E2E8F0", 10f, 1)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(6)
                    }
                }

                val tvL = TextView(context).apply {
                    text = label
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tvL)

                val tvVal = TextView(context).apply {
                    text = initialValueText
                    setTextColor(Color.parseColor("#047857"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(8), 0, dp(8), 0)
                }

                val btnMinus = TextView(context).apply {
                    text = "−"
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    background = roundedBg("#E2E8F0", 6f)
                    setOnClickListener { tvVal.text = onMinus() }
                }

                val btnPlus = TextView(context).apply {
                    text = "+"
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    background = roundedBg("#E2E8F0", 6f)
                    setOnClickListener { tvVal.text = onPlus() }
                }

                row.addView(btnMinus)
                row.addView(tvVal)
                row.addView(btnPlus)
                return row
            }

            editorLayout.addView(createStepperRow("R$/Km Mín:", String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", curKm), {
                curKm = (curKm - 0.10).coerceAtLeast(0.50)
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", curKm)
            }, {
                curKm = (curKm + 0.10).coerceAtMost(10.00)
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", curKm)
            }))

            editorLayout.addView(createStepperRow("R$/Hora Mín:", String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f/h", curHr), {
                curHr = (curHr - 5.0).coerceAtLeast(10.0)
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f/h", curHr)
            }, {
                curHr = (curHr + 5.0).coerceAtMost(200.0)
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f/h", curHr)
            }))

            editorLayout.addView(createStepperRow("Nota Mínima:", String.format(Locale.forLanguageTag("pt-BR"), "%.2f", curRating), {
                curRating = (curRating - 0.05).coerceAtLeast(4.00)
                String.format(Locale.forLanguageTag("pt-BR"), "%.2f", curRating)
            }, {
                curRating = (curRating + 0.05).coerceAtMost(5.00)
                String.format(Locale.forLanguageTag("pt-BR"), "%.2f", curRating)
            }))

            editorLayout.addView(createStepperRow("Emb. Longe Máx:", String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", curPickup), {
                curPickup = (curPickup - 0.5).coerceAtLeast(0.5)
                String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", curPickup)
            }, {
                curPickup = (curPickup + 0.5).coerceAtMost(8.0)
                String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", curPickup)
            }))

            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }

            val btnBack = TextView(context).apply {
                text = "Voltar"
                setTextColor(Color.parseColor("#475569"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = roundedBg("#F1F5F9", 8f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener { showHubView() }
            }
            actionsRow.addView(btnBack)

            val btnSave = TextView(context).apply {
                text = "Salvar Semáforo"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = roundedBg("#10B981", 8f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                setOnClickListener {
                    onUserUpdatedSemaforoQuickly?.invoke(curKm, curHr, curRating, curPickup)
                    android.widget.Toast.makeText(context, "✓ Semáforo e Embarque Atualizados!", android.widget.Toast.LENGTH_SHORT).show()
                    showHubView()
                }
            }
            actionsRow.addView(btnSave)

            editorLayout.addView(actionsRow)
            contentContainer.addView(editorLayout)
        }

        showHubView()
        root.addView(contentContainer)
        return root
    }

    private fun launchAppScreen(screenName: String) {
        try {
            val intent = android.content.Intent(context, com.creativeam.goorno.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_SCREEN", screenName)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
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
                                } catch (_: Throwable) {
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
                            return true
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            v?.performClick()
                        }
                        return false
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

        val (defaultGravity, defaultX) = when (overlayHorizontalPosition) {
            "LEFT" -> Pair(Gravity.TOP or Gravity.START, dp(10))
            "CENTER" -> Pair(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0)
            else -> Pair(Gravity.TOP or Gravity.END, dp(10))
        }
        val defaultY = if (overlayBubblePosition == "ABOVE") dp(50) else dp(110)

        val savedX = if (overlayHorizontalPosition == "CENTER" && !posPrefs.contains("saved_x")) 0 else posPrefs.getInt("saved_x", defaultX)
        val savedY = posPrefs.getInt("saved_y", defaultY)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = defaultGravity
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
            TrafficLightVerdict.GREEN -> Triple("#00C853", "#F0FDF4", "#86EFAC")
            TrafficLightVerdict.YELLOW -> Triple("#FACC15", "#FFFBEB", "#FCD34D")
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

    private fun roundedLeftBg(hexColor: String, radiusDp: Float): GradientDrawable {
        val r = dp(radiusDp.toInt()).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun roundedBottomBg(hexColor: String, radiusDp: Float): GradientDrawable {
        val r = dp(radiusDp.toInt()).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            setColor(Color.parseColor(hexColor))
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
