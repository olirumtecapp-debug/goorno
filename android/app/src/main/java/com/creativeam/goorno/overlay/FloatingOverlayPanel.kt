package com.creativeam.goorno.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.model.DriverFinancialConfig
import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.OverlayDisplayStyle
import com.creativeam.goorno.model.PassengerRatingVerdict
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.TrafficLightVerdict
import com.creativeam.goorno.service.CopilotNotificationReceiver
import com.creativeam.goorno.util.StreetViewLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var displayStyle: OverlayDisplayStyle = OverlayDisplayStyle.COMPLETO
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
        activeCardFingerprint = ""
        offerQueue = emptyList()
        currentQueueIndex = 0
        recentlyDismissedOfferTimestamps.clear()
        isTransitioningBetweenOffers = false
        mainHandler.removeCallbacks(countdownRunnable)
        mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
        if (!enabled) {
            removeWindowViewOnly()
            hidePanel()
        }
        mainHandler.post {
            updateShortcutBubbleVisibility()
        }
    }

    fun isCopilotMonitoringEnabled(): Boolean = isMonitoringEnabled

    fun isCardCurrentlyVisible(): Boolean = isAdded || isTransitioningBetweenOffers

    fun getActiveCardFingerprint(): String = activeCardFingerprint

    fun getVisibleOfferSourceApp(): com.creativeam.goorno.model.SourceApp? {
        return offerQueue.getOrNull(currentQueueIndex)?.offer?.sourceApp
    }

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
            val appPriceKey = "${firstEval.offer.sourceApp.name}|${String.format(Locale.US, "%.2f", newPrice)}"

            val now = System.currentTimeMillis()
            recentlyDismissedOfferTimestamps.entries.removeAll { now - it.value > 20_000L }

            // Proteção Anti-Reabertura: se esta oferta já foi fechada/expirada recentemente,
            // ignora novas leituras por 5s (evita reabrir em loop na mesma tela)
            val lastDismissed = recentlyDismissedOfferTimestamps[newFingerprint] ?: recentlyDismissedOfferTimestamps[appPriceKey]
            if (lastDismissed != null && (now - lastDismissed) < 5_000L && results.none { it.offer.isSimulated }) {
                return@post
            }

            // Proteção Anti-Flicker: se o card flutuante já está visível para a mesma corrida,
            // atualiza os dados preservando a contagem regressiva se campos novos forem identificados
            if (isCardCurrentlyVisible() && offerQueue.isNotEmpty()) {
                val currentActive = offerQueue.getOrNull(currentQueueIndex)
                if (currentActive != null && currentActive.offer.sourceApp == firstEval.offer.sourceApp) {
                    val currentPrice = currentActive.offer.offeredPrice ?: 0.0
                    if (kotlin.math.abs(currentPrice - newPrice) < 0.25) {
                        // É a mesma corrida recebendo dados complementares (ex: endereços, paradas, supermercado ou rating)
                        val prevRating = currentActive.passengerRating
                        val newRating = firstEval.passengerRating
                        val hasNewData = (prevRating == null && newRating != null) ||
                            (currentActive.offer.originAddress.isNullOrBlank() && !firstEval.offer.originAddress.isNullOrBlank()) ||
                            (currentActive.offer.destinationAddress.isNullOrBlank() && !firstEval.offer.destinationAddress.isNullOrBlank()) ||
                            (currentActive.offer.stopsDescription.isNullOrBlank() && !firstEval.offer.stopsDescription.isNullOrBlank()) ||
                            (!currentActive.offer.isSupermarket && firstEval.offer.isSupermarket) ||
                            (currentActive.totalConsideredDistanceKm == null && firstEval.totalConsideredDistanceKm != null)

                        offerQueue = results.take(5)
                        activeCardFingerprint = newFingerprint
                        if (hasNewData) {
                            val savedSeconds = remainingSeconds
                            showCurrentQueuedOffer()
                            remainingSeconds = savedSeconds
                        }
                        return@post
                    }
                }
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

    /**
     * Atualiza somente avaliações/nota dos cartões já enfileirados, mantendo
     * o card atual, sua posição e a contagem regressiva. O OCR suplementar
     * nunca deve fazer a fila retornar ao primeiro chamado.
     */
    fun updateRatingsWithoutReset(results: List<FinancialEvaluationResult>) {
        if (results.isEmpty()) return
        mainHandler.post {
            if (!isCardCurrentlyVisible() || offerQueue.size != results.size) return@post
            val sameOffers = offerQueue.zip(results).all { (before, after) ->
                val beforePrice = before.offer.offeredPrice ?: 0.0
                val afterPrice = after.offer.offeredPrice ?: 0.0
                val beforeKm = before.totalConsideredDistanceKm ?: 0.0
                val afterKm = after.totalConsideredDistanceKm ?: 0.0
                before.offer.sourceApp == after.offer.sourceApp &&
                    kotlin.math.abs(beforePrice - afterPrice) < 0.02 &&
                    kotlin.math.abs(beforeKm - afterKm) < 0.16
            }
            if (!sameOffers) return@post
            val current = currentQueueIndex
            val oldItem = offerQueue.getOrNull(current)
            val newItem = results.getOrNull(current)
            val hasEnrichedData = oldItem != null && newItem != null && (
                oldItem.passengerRating != newItem.passengerRating ||
                (oldItem.offer.stopsDescription.isNullOrBlank() && !newItem.offer.stopsDescription.isNullOrBlank()) ||
                (oldItem.offer.originAddress.isNullOrBlank() && !newItem.offer.originAddress.isNullOrBlank()) ||
                (oldItem.offer.destinationAddress.isNullOrBlank() && !newItem.offer.destinationAddress.isNullOrBlank()) ||
                (!oldItem.offer.isSupermarket && newItem.offer.isSupermarket)
            )
            offerQueue = results.take(5)
            if (hasEnrichedData && current in offerQueue.indices) {
                val secondsLeft = remainingSeconds
                showCurrentQueuedOffer()
                remainingSeconds = secondsLeft
                activeTimerLabel?.text = "${remainingSeconds}s"
            }
        }
    }

    private fun showCurrentQueuedOffer() {
        if (currentQueueIndex >= offerQueue.size) {
            hidePanel()
            onOfferCardClosed?.invoke()
            return
        }

        val currentEval = offerQueue[currentQueueIndex]
        val cardPrice = currentEval.offer.offeredPrice ?: 0.0
        val cardKm = currentEval.totalConsideredDistanceKm ?: 0.0
        activeCardFingerprint = "${currentEval.offer.sourceApp.name}|${String.format(Locale.US, "%.2f", cardPrice)}|${String.format(Locale.US, "%.1f", cardKm)}"
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
            OverlayDisplayStyle.COMPLETO -> buildCompletoCardView(currentEval, oneBasedIndex, totalInQueue)
            OverlayDisplayStyle.SECUNDARIO -> buildSecundarioSidebarView(currentEval, oneBasedIndex, totalInQueue)
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
        val nextIndex = OfferQueueProgression.nextIndex(currentQueueIndex, offerQueue.size)
        if (nextIndex != null) {
            currentQueueIndex = nextIndex
            removeWindowViewOnly()
            isTransitioningBetweenOffers = true
            mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
            mainHandler.postDelayed(showNextOfferAfterPauseRunnable, 350L)
        } else {
            offerQueue = emptyList()
            currentQueueIndex = 0
            hidePanel()
            onOfferCardClosed?.invoke()
        }
    }

    fun dismissCurrentOfferCardByUser() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { dismissCurrentOfferCardByUser() }
            return
        }
        val now = System.currentTimeMillis()
        if (activeCardFingerprint.isNotBlank()) {
            recentlyDismissedOfferTimestamps[activeCardFingerprint] = now
        }
        val currentActive = offerQueue.getOrNull(currentQueueIndex)
        if (currentActive != null) {
            val price = currentActive.offer.offeredPrice ?: 0.0
            val appPriceKey = "${currentActive.offer.sourceApp.name}|${String.format(Locale.US, "%.2f", price)}"
            recentlyDismissedOfferTimestamps[appPriceKey] = now
        }
        activeCardFingerprint = ""
        offerQueue = emptyList()
        currentQueueIndex = 0
        isTransitioningBetweenOffers = false
        mainHandler.removeCallbacks(countdownRunnable)
        mainHandler.removeCallbacks(showNextOfferAfterPauseRunnable)
        try {
            rootContainer?.visibility = View.GONE
        } catch (_: Throwable) {}
        removeWindowViewOnly()
        reconcileShortcutBubbleState()
        onOfferCardClosed?.invoke()
    }

    fun hideOfferCardOnly() {
        dismissCurrentOfferCardByUser()
    }

    private fun removeWindowViewOnly() {
        val container = rootContainer ?: return
        try {
            container.visibility = View.GONE
            if (container.isAttachedToWindow || isAdded) {
                windowManager.removeView(container)
            }
        } catch (_: Throwable) {
        } finally {
            isAdded = false
            rootContainer = null
            RuntimeDiagnosticsBus.setOverlayVisible(false)
        }
    }

    fun hidePanel() {
        mainHandler.post {
            val now = System.currentTimeMillis()
            if (activeCardFingerprint.isNotBlank()) {
                recentlyDismissedOfferTimestamps[activeCardFingerprint] = now
            }
            val currentActive = offerQueue.getOrNull(currentQueueIndex)
            if (currentActive != null) {
                val price = currentActive.offer.offeredPrice ?: 0.0
                val appPriceKey = "${currentActive.offer.sourceApp.name}|${String.format(Locale.US, "%.2f", price)}"
                recentlyDismissedOfferTimestamps[appPriceKey] = now
            }
            activeCardFingerprint = ""
            offerQueue = emptyList()
            currentQueueIndex = 0
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
            activeCardFingerprint = ""
            offerQueue = emptyList()
            currentQueueIndex = 0
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
        val cardBgHex = "#0C0D11" // Preto absoluto e puro
        val primaryTextHex = "#FFFFFF"
        val labelTextHex = "#94A3B8"
        val subTextHex = "#E2E8F0"

        val verdictColorHex = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "#10E548" // Verde Neon ultra vivo
            TrafficLightVerdict.YELLOW -> "#FFB800" // Âmbar Elétrico
            TrafficLightVerdict.RED -> "#FF3B30" // Vermelho Coral puro
            TrafficLightVerdict.GRAY_NEUTRAL -> "#64748B"
        }

        val kmBarColor = when {
            grossKm >= targetKm -> "#10E548"
            grossKm >= minKm -> "#FFB800"
            else -> "#FF3B30"
        }
        val hrBarColor = when {
            grossHr >= targetHr -> "#10E548"
            grossHr >= minHr -> "#FFB800"
            else -> "#FF3B30"
        }
        val ratingBarColor = when (result.passengerRatingVerdict) {
            PassengerRatingVerdict.GOOD -> "#10E548"
            PassengerRatingVerdict.MEDIUM -> "#FFB800"
            PassengerRatingVerdict.BAD -> "#FF3B30"
            else -> "#64748B"
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        // Card Container Horizontal Enxuto (Borda viva 2dp na cor do semáforo, altura dinâmica sem corte)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedCardBg(cardBgHex, verdictColorHex, 16f, 2)
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
                layoutParams = LinearLayout.LayoutParams(dp(5), dp(22)).apply {
                    marginEnd = dp(5)
                }
            }
            val tvVal = TextView(context).apply {
                text = value
                setTextColor(Color.parseColor(primaryTextHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
                isSingleLine = true
                maxLines = 1
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
        val ratStr = if (result.offer.passengerIsNew || (result.passengerRating != null && result.passengerRating < 0.0)) "Novo" else if (result.passengerRating != null) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating) else "--"

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

        val offeredPrice = offer.offeredPriceField.valueOrNull() ?: result.offer.offeredPriceField.valueOrNull()
        val pricePrefix = if (offeredPrice != null && offeredPrice > 0.0) {
            String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f • ", offeredPrice)
        } else ""

        val tvSub = TextView(context).apply {
            text = "$pricePrefix$minStr • $distStr"
            setTextColor(Color.parseColor(subTextHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
            maxLines = 1
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
        activeTimerLabel = tvTimer
        subRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBg("#262930", 6f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
                minWidth = dp(34)
                minHeight = dp(28)
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                    dismissCurrentOfferCardByUser()
                }
                true
            }
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
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { dismissCurrentOfferCardByUser() }
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                    dismissCurrentOfferCardByUser()
                }
                true
            }
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
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                minWidth = dp(32)
                minHeight = dp(28)
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                    dismissCurrentOfferCardByUser()
                }
                true
            }
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
        val ratingText = when {
            result.offer.passengerIsNew || (result.passengerRating != null && result.passengerRating < 0.0) -> "Novo"
            result.passengerRating != null -> String.format(Locale.forLanguageTag("pt-BR"), "%.2f", result.passengerRating)
            else -> "--"
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
        val text: String,
        val bgHex: String,
        val textColorHex: String = "#FFFFFF",
        val strokeHex: String? = null
    )

    private fun buildAuxiliaryAlertPill(
        result: FinancialEvaluationResult,
        isDark: Boolean
    ): View? {
        val alerts = mutableListOf<AlertPillItem>()

        // 1. Área de Risco (Alerta prioritário de segurança - Estilo Imagem 4)
        if (result.hasRiskAlert) {
            val riskText = result.riskAlertBadge?.takeIf { it.isNotBlank() } ?: "Área de Risco"
            alerts.add(
                AlertPillItem(
                    icon = "🚨",
                    text = riskText.uppercase(),
                    bgHex = "#DC2626",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 2. Rodízio SP no Destino / Origem (Estilo Imagem 5)
        if (result.isRodizioAlert) {
            val rText = result.rodizioAlertBadge?.takeIf { it.isNotBlank() } ?: "RODÍZIO NO DESTINO"
            alerts.add(
                AlertPillItem(
                    icon = "⚠️",
                    text = rText.uppercase(),
                    bgHex = "#D97706",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 2b. Trânsito Lento ou Corrida Travada (Alerta inteligente de velocidade/tempo excessivo)
        if (result.isSlowTraffic) {
            val label = result.slowTrafficBadge?.takeIf { it.isNotBlank() } ?: "TRÂNSITO LENTO"
            alerts.add(
                AlertPillItem(
                    icon = if (result.isSeverelyStuck) "🛑" else "🐢",
                    text = label.uppercase(),
                    bgHex = if (result.isSeverelyStuck) "#991B1B" else "#C2410C",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 3. Supermercado / Atacadista (Estilo Imagem 1)
        if (result.isSupermarket) {
            val sName = result.supermarketName?.takeIf { it.isNotBlank() } ?: "MERCADO"
            val label = if (sName.contains("mercado", ignoreCase = true) || sName.length <= 12) sName else "MERCADO"
            alerts.add(
                AlertPillItem(
                    icon = "🛒",
                    text = label.uppercase(),
                    bgHex = "#DC2626",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 4. Corrida Curta (Estilo Imagem 2)
        if (result.isShortTrip) {
            alerts.add(
                AlertPillItem(
                    icon = "⚡",
                    text = "CORRIDA CURTA",
                    bgHex = "#0284C7",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 5. Embarque Distante (Estilo Imagem 3)
        if (result.isDistantPickup) {
            alerts.add(
                AlertPillItem(
                    icon = "⚠️",
                    text = "EMBARQUE DISTANTE",
                    bgHex = "#0F172A",
                    textColorHex = "#FFFFFF",
                    strokeHex = "#334155"
                )
            )
        }

        // 6. Região Favorita
        if (result.isFavoriteZone && !result.hasRiskAlert) {
            val favText = result.favoriteZoneBadge?.takeIf { it.isNotBlank() } ?: "REGIÃO FAVORITA"
            alerts.add(
                AlertPillItem(
                    icon = "⭐",
                    text = favText.uppercase(),
                    bgHex = "#059669",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 7. Paradas Intermediárias
        if (!result.stopsDescription.isNullOrBlank()) {
            alerts.add(
                AlertPillItem(
                    icon = "🛑",
                    text = result.stopsDescription.uppercase(),
                    bgHex = "#0F766E",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 8. Viagem Longa
        if (result.isLongTrip && alerts.size < 2) {
            alerts.add(
                AlertPillItem(
                    icon = "⏱️",
                    text = "VIAGEM LONGA",
                    bgHex = "#6366F1",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 9. Chamado Repetido
        if (result.isRepeatedOffer && alerts.size < 2) {
            alerts.add(
                AlertPillItem(
                    icon = "🔁",
                    text = "CHAMADO REPETIDO",
                    bgHex = "#EA580C",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 10. Nota Baixa
        if (result.passengerRatingVerdict == PassengerRatingVerdict.BAD && alerts.size < 2) {
            val rat = result.passengerRating?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.2f", it) } ?: ""
            alerts.add(
                AlertPillItem(
                    icon = "👤",
                    text = "NOTA BAIXA ($rat)",
                    bgHex = "#B45309",
                    textColorHex = "#FFFFFF"
                )
            )
        }

        // 11. Fallback Operacional caso haja outro aviso não mapeado
        if (alerts.isEmpty() && !result.operationalBadge.isNullOrBlank()) {
            alerts.add(
                AlertPillItem(
                    icon = "⚡",
                    text = result.operationalBadge.uppercase(),
                    bgHex = "#D97706",
                    textColorHex = "#FFFFFF"
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
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }

        alerts.take(2).forEachIndexed { i, alert ->
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = roundedCardBg(alert.bgHex, alert.strokeHex ?: alert.bgHex, 6f, if (alert.strokeHex != null) 1 else 0)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (i > 0) topMargin = dp(3)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = dp(3).toFloat()
                }
            }

            val tvContent = TextView(context).apply {
                text = "${alert.icon} ${alert.text}"
                setTextColor(Color.parseColor(alert.textColorHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                setSingleLine(true)
                gravity = Gravity.CENTER
            }
            pill.addView(tvContent)
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
                text = if (result.favoriteZoneBadge.startsWith("⭐")) result.favoriteZoneBadge else "⭐ " + result.favoriteZoneBadge
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
            TrafficLightVerdict.GREEN -> "#10E548" // Verde Neon ultra vivo
            TrafficLightVerdict.YELLOW -> "#FFB800" // Âmbar Elétrico
            TrafficLightVerdict.RED -> "#FF3B30" // Vermelho Coral puro
            TrafficLightVerdict.GRAY_NEUTRAL -> "#64748B"
        }

        val cardBgHex = "#0C0D11" // Preto absoluto e puro
        val subTextHex = "#CBD5E1"

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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
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
            text = "$minStr • $distStr"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
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

        // 2. BLOCO DA DIREITA: BLOCO SÓLIDO DE COR INTEGRADO (SEM TEXTO, SÓ A COR)
        val rightColorBlock = View(context).apply {
            background = roundedRightBg(verdictColorHex, 16f)
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
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
    private fun buildCompletoCardView(
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

        val theme = lastKnownConfig?.overlayTheme?.uppercase() ?: "COLORIDO"
        val isColorido = theme == "COLORIDO"
        val isClaro = theme == "CLARO" || theme == "LIGHT"
        val fontSizePref = lastKnownConfig?.overlayFontSize?.uppercase() ?: "G"
        val fontFamilyPref = lastKnownConfig?.overlayFontFamily ?: "SISTEMA"
        val cardTypeface = getOverlayTypeface(fontFamilyPref)
        val pillarValSp = when (fontSizePref) {
            "M" -> 22f
            "G" -> 25f
            "GG", "GGG" -> 29f
            else -> 25f
        }
        val pillarLblSp = when (fontSizePref) {
            "M" -> 11f
            "G" -> 12f
            "GG", "GGG" -> 13f
            else -> 12f
        }
        val summaryValSp = when (fontSizePref) {
            "M" -> 14.5f
            "G" -> 15.5f
            "GG", "GGG" -> 17f
            else -> 15.5f
        }

        val verdictHex = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "#16A34A"
            TrafficLightVerdict.YELLOW -> "#EAB308"
            TrafficLightVerdict.RED -> "#DC2626"
            TrafficLightVerdict.GRAY_NEUTRAL -> "#475569"
        }

        val cardBg = when {
            isColorido -> verdictHex
            isClaro -> "#FFFFFF"
            else -> "#1E2024"
        }
        val cardBorder = when {
            isColorido -> when (result.verdict) {
                TrafficLightVerdict.GREEN -> "#14532D" // Borda e borda inferior nitidamente mais escura
                TrafficLightVerdict.YELLOW -> "#78350F" // Borda e borda inferior ambar escura
                TrafficLightVerdict.RED -> "#7F1D1D"   // Borda e borda inferior vinho escura
                TrafficLightVerdict.GRAY_NEUTRAL -> "#1E293B"
            }
            else -> verdictHex
        }

        val primaryTextColor = if (isClaro) Color.parseColor("#0F172A") else Color.parseColor("#F1F5F9")
        val secondaryTextColor = when {
            isColorido -> Color.parseColor("#E2E8F0")
            isClaro -> Color.parseColor("#64748B")
            else -> Color.parseColor("#94A3B8")
        }

        val kmBarColor = when {
            grossKm >= targetKm -> "#22C55E"
            grossKm >= minKm -> "#EAB308"
            else -> "#EF4444"
        }
        val hrBarColor = when {
            grossHr >= targetHr -> "#22C55E"
            grossHr >= minHr -> "#EAB308"
            else -> "#EF4444"
        }
        val ratingBadge = result.passengerRatingBadge.orEmpty()
        val ratingClean = ratingBadge.replace(Regex("\\s*\\([^)]*\\)"), "").replace("★", "").replace("🟢", "").replace("🟡", "").replace("🔴", "").trim()
        val ratingNum = offer.passengerRating ?: ratingClean.replace(",", ".").toDoubleOrNull()
        val isNewPassenger = offer.passengerIsNew || (ratingNum != null && ratingNum < 0.0) || ratingClean.equals("novo", ignoreCase = true)
        val ratingBarColor = when {
            isNewPassenger -> "#38BDF8"
            ratingNum != null && ratingNum >= 4.85 -> "#22C55E"
            ratingNum != null && ratingNum >= 4.70 -> "#EAB308"
            ratingNum != null && ratingNum > 0.0 -> "#EF4444"
            else -> "#94A3B8"
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener(createDragOrTapTouchListener())
        }

        val cardWidthDp = 310
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            // Borda com lip inferior mais grosso (6dp) e borda mais escura
            background = roundedCardWithThickBottomBg(cardBg, cardBorder, 18f, strokeDp = 2, bottomLipDp = 6)
            layoutParams = LinearLayout.LayoutParams(dp(cardWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // Top Row: [Badge App] + Time/Distance + Timer + Close
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

        val is99 = offer.sourceApp.displayName.contains("99", ignoreCase = true)
        val tvAppBadge = TextView(context).apply {
            text = if (is99) "99" else "Uber"
            setTextColor(if (is99) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = if (is99) roundedBg("#FACC15", 6f) else roundedBg("#000000", 6f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }
        topRow.addView(tvAppBadge)

        val distStr = if (totalKm > 0.0) {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm)
        } else "-- km"
        val minStr = if (totalMin > 0) "${totalMin} min" else "-- min"
        val seqTag = if (totalCount > 1) "[$index/$totalCount] " else ""

        val tvSummary = TextView(context).apply {
            text = "$seqTag$minStr • $distStr"
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, summaryValSp)
            typeface = cardTypeface
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvSummary)

        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), 0)
        }
        activeTimerLabel = tvTimer
        topRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕ "
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { dismissCurrentOfferCardByUser() }
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                    dismissCurrentOfferCardByUser()
                }
                true
            }
        }
        topRow.addView(btnClose)
        card.addView(topRow)

        // 3 Pillars Row: [R$/Km] | [R$/Hora] | [Nota]
        val pillarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createPillarCol(label: String, barColorHex: String, valueText: String): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                val tvL = TextView(context).apply {
                    text = label
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, pillarLblSp)
                    typeface = cardTypeface
                    gravity = Gravity.CENTER
                }
                addView(tvL)

                val valRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, 0)
                }

                // Barrinha semaforica individual com cantos arredondados antes do valor
                val bar = View(context).apply {
                    background = roundedBg(barColorHex, 2f)
                    layoutParams = LinearLayout.LayoutParams(dp(4), dp(20)).apply {
                        marginEnd = dp(4)
                    }
                }
                valRow.addView(bar)

                val tvV = TextView(context).apply {
                    text = valueText
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, pillarValSp)
                    typeface = cardTypeface
                    gravity = Gravity.CENTER
                }
                valRow.addView(tvV)

                addView(valRow)
            }
        }

        val kmVal = if (grossKm > 0) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", grossKm) else "--"
        val hrVal = if (grossHr > 0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f", grossHr) else "--"
        val ratingVal = if (isNewPassenger) "Novo" else if (ratingClean.isNotBlank() && ratingClean != "--") ratingClean else if (ratingNum != null && ratingNum > 0.0) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratingNum) else "--"

        pillarsRow.addView(createPillarCol("R$/Km", kmBarColor, kmVal))
        pillarsRow.addView(createPillarCol("R$/Hora", hrBarColor, hrVal))
        pillarsRow.addView(createPillarCol("Nota", ratingBarColor, ratingVal))
        card.addView(pillarsRow)

        // Adiciona o card limpo (sem tags de alerta dentro)
        root.addView(card)

        // ALERTAS DESTACADOS TOTALMENTE FORA DO CARD (PILULAS COLORIDAS DEDICADAS)
        if (result.hasRiskAlert && !result.riskAlertBadge.isNullOrBlank()) {
            val tvRisk = TextView(context).apply {
                text = if (result.riskAlertBadge.startsWith("⚠️")) result.riskAlertBadge else "⚠️ " + result.riskAlertBadge
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#DC2626", "#7F1D1D", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvRisk)
        }

        if (!result.stopsDescription.isNullOrBlank()) {
            val tvStops = TextView(context).apply {
                val desc = result.stopsDescription
                text = if (desc.startsWith("🛑")) desc else "🛑 $desc"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#C2410C", "#7C2D12", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvStops)
        }

        if (result.isLongTrip) {
            val tvLong = TextView(context).apply {
                text = "⏱️ Viagem Longa (> 45 min)"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#4F46E5", "#312E81", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvLong)
        }

        if (result.isDistantPickup) {
            val tvPickup = TextView(context).apply {
                text = "⚠️ Embarque Distante"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#B45309", "#78350F", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvPickup)
        }

        if (result.isSupermarket && !result.supermarketName.isNullOrBlank()) {
            val tvMarket = TextView(context).apply {
                text = "🛒 ${result.supermarketName}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#1D4ED8", "#1E3A8A", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvMarket)
        }

        if (result.isRodizioAlert && !result.rodizioAlertBadge.isNullOrBlank()) {
            val tvRodizio = TextView(context).apply {
                val b = result.rodizioAlertBadge
                text = if (b.startsWith("🚗")) b else "🚗 $b"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#B45309", "#78350F", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvRodizio)
        }

        if (result.isFavoriteZone && !result.favoriteZoneBadge.isNullOrBlank() && !result.hasRiskAlert) {
            val tvFav = TextView(context).apply {
                text = if (result.favoriteZoneBadge.startsWith("⭐")) result.favoriteZoneBadge else "⭐ ${result.favoriteZoneBadge}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = roundedCardBg("#15803D", "#14532D", 10f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            root.addView(tvFav)
        }

        return root
    }

    private fun buildSecundarioSidebarView(
        result: FinancialEvaluationResult,
        index: Int,
        totalCount: Int
    ): View {
        val offer = result.offer
        val grossKm = result.grossPerKm ?: 0.0
        val grossHr = result.grossPerHour ?: 0.0

        val verdictHex = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "#16A34A"
            TrafficLightVerdict.YELLOW -> "#CA8A04"
            TrafficLightVerdict.RED -> "#DC2626"
            TrafficLightVerdict.GRAY_NEUTRAL -> "#475569"
        }
        val verdictLabel = when (result.verdict) {
            TrafficLightVerdict.GREEN -> "✓ ACEITAR"
            TrafficLightVerdict.YELLOW -> "● ATENÇÃO"
            else -> "✕ RECUSAR"
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setOnTouchListener(createDragOrTapTouchListener())
        }

        val timerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, dp(4), dp(2))
        }
        val tvTimer = TextView(context).apply {
            text = "${remainingSeconds}s"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), 0)
        }
        activeTimerLabel = tvTimer
        timerRow.addView(tvTimer)

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        timerRow.addView(btnClose)
        root.addView(timerRow)

        val cardTypeface = getOverlayTypeface(lastKnownConfig?.overlayFontFamily ?: "ROBOTO")

        val decisionPill = TextView(context).apply {
            text = verdictLabel
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = cardTypeface
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg(verdictHex, 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        root.addView(decisionPill)

        fun createDarkMetricPill(valueStr: String, unitStr: String): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = roundedCardBg("#1E2024", "#334155", 14f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }

                val tvVal = TextView(context).apply {
                    text = valueStr
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    typeface = cardTypeface
                }
                addView(tvVal)

                val tvUnit = TextView(context).apply {
                    text = unitStr
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(3), dp(2), 0, 0)
                }
                addView(tvUnit)
            }
        }

        val kmStr = if (grossKm > 0) String.format(Locale.forLanguageTag("pt-BR"), "%.2f", grossKm) else "--"
        val hrStr = if (grossHr > 0) String.format(Locale.forLanguageTag("pt-BR"), "%.0f", grossHr) else "--"
        val ratingStr = result.passengerRatingBadge?.takeIf { it.isNotBlank() } ?: "--"

        root.addView(createDarkMetricPill(kmStr, " /km"))
        root.addView(createDarkMetricPill(hrStr, " /h"))
        root.addView(createDarkMetricPill(ratingStr, " nota"))

        if (result.isRiskZone) {
            val tvRisk = TextView(context).apply {
                text = "⚠️ Área de Risco"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedCardBg("#DC2626", "#7F1D1D", 8f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            root.addView(tvRisk)
        }

        if (!result.stopsDescription.isNullOrBlank()) {
            val tvStops = TextView(context).apply {
                val desc = result.stopsDescription
                text = if (desc.startsWith("🛑")) desc else "🛑 $desc"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedCardBg("#C2410C", "#7C2D12", 8f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            root.addView(tvStops)
        }

        if (result.isLongTrip) {
            val tvLong = TextView(context).apply {
                text = "⏱️ Viagem Longa"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedCardBg("#4F46E5", "#312E81", 8f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            root.addView(tvLong)
        }

        if (result.isSupermarket) {
            val tvMarket = TextView(context).apply {
                val sName = result.supermarketName ?: "Supermercado"
                text = if (sName.startsWith("🛒")) sName else "🛒 $sName"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedCardBg("#0D9488", "#115E59", 8f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            root.addView(tvMarket)
        }

        if (result.isDistantPickup) {
            val tvPickup = TextView(context).apply {
                text = "⚠️ Embarque Distante"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = roundedCardBg("#B45309", "#78350F", 8f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            root.addView(tvPickup)
        }

        return root
    }

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

            // CRITICAL: Blindagem total contra interferência na leitura de acessibilidade da Uber e 99!
            // Torna o botão flutuante e todos os seus filhos 100% invisíveis/transparentes para a árvore de acessibilidade do Android.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

            val logoIv = android.widget.ImageView(context).apply {
                setImageResource(com.creativeam.goorno.R.drawable.logo_goorno)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
            background = roundedCardBg("#FFFFFF", "#E2E8F0", 22f, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(16).toFloat()
            }
        }

        // Header: Logo/Icon + GoOrNo + Subtitle + Close Button
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
            text = "GoOrNo"
            setTextColor(Color.parseColor("#0F2F28"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvSub = TextView(context).apply {
            text = if (isMonitoringEnabled) "● Semáforo lendo ofertas" else "● Semáforo pausado"
            setTextColor(if (isMonitoringEnabled) Color.parseColor("#059669") else Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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

        // Quick Toggles Row: [ 📹 Câmera ] [ 🚦 Semáforo ] [ 🎯 Metas ]
        val togglesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        fun createQuickCard(icon: String, title: String, subtitle: String, isHighlighted: Boolean, onClick: (View) -> Unit): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = if (isHighlighted) {
                    roundedCardBg("#F0FDF4", "#10B981", 14f, 1)
                } else {
                    roundedCardBg("#F8FAFC", "#E2E8F0", 14f, 1)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { onClick(this) }

                val tvI = TextView(context).apply {
                    text = icon
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    gravity = Gravity.CENTER
                }
                addView(tvI)

                val tvT = TextView(context).apply {
                    text = title
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, 0)
                }
                addView(tvT)

                val tvS = TextView(context).apply {
                    text = subtitle
                    setTextColor(if (isHighlighted) Color.parseColor("#059669") else Color.parseColor("#64748B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                addView(tvS)
            }
        }

        // 1. CÂMERA SECRETA CARD (Rota Pro UX: Vermelho quando gravando, alterna câmera)
        val isCamRecording = com.creativeam.goorno.camera.SecretCameraRecordingService.isRecordingState.value
        val camCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = if (isCamRecording) {
                roundedCardBg("#FEF2F2", "#EF4444", 14f, 1)
            } else {
                roundedCardBg("#F8FAFC", "#E2E8F0", 14f, 1)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }

            val tvI = TextView(context).apply {
                text = "📹"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
            }
            addView(tvI)

            val tvT = TextView(context).apply {
                text = "Câmera"
                setTextColor(Color.parseColor("#0F2F28"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            addView(tvT)

            val tvS = TextView(context).apply {
                text = if (isCamRecording) "Gravando" else "Desligada"
                setTextColor(if (isCamRecording) Color.parseColor("#DC2626") else Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(tvS)

            setOnClickListener {
                val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCam || !hasAudio) {
                    launchAppScreen("SECRET_CAMERA")
                    dismissQuickSettingsModal()
                    android.widget.Toast.makeText(context, "Conceda permissão de Câmera e Microfone no GoOrNo", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    if (com.creativeam.goorno.camera.SecretCameraRecordingService.isRecordingState.value) {
                        com.creativeam.goorno.camera.SecretCameraRecordingService.stop(context)
                        tvS.text = "Desligada"
                        tvS.setTextColor(Color.parseColor("#64748B"))
                        background = roundedCardBg("#F8FAFC", "#E2E8F0", 14f, 1)
                        android.widget.Toast.makeText(context, "Câmera Secreta DESLIGADA", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        com.creativeam.goorno.camera.SecretCameraRecordingService.start(context)
                        tvS.text = "Gravando"
                        tvS.setTextColor(Color.parseColor("#DC2626"))
                        background = roundedCardBg("#FEF2F2", "#EF4444", 14f, 1)
                        android.widget.Toast.makeText(context, "🔴 Câmera Secreta GRAVANDO!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        togglesRow.addView(camCard)

        // 2. SEMÁFORO DE VALORES CARD
        lateinit var semaforoCard: View
        semaforoCard = createQuickCard(
            "🚦",
            "Semáforo",
            if (isMonitoringEnabled) "Ligado" else "Desligado",
            isMonitoringEnabled
        ) {
            val newState = !isMonitoringEnabled
            setMonitoringEnabled(newState, accessibilityEnabled = true)
            tvSub.text = if (newState) "● Semáforo lendo ofertas" else "● Semáforo pausado"
            tvSub.setTextColor(if (newState) Color.parseColor("#059669") else Color.parseColor("#64748B"))
            (semaforoCard as LinearLayout).background = if (newState) {
                roundedCardBg("#F0FDF4", "#10B981", 14f, 1)
            } else {
                roundedCardBg("#F8FAFC", "#E2E8F0", 14f, 1)
            }
            val subTextChild = (semaforoCard as LinearLayout).getChildAt(2) as? TextView
            subTextChild?.text = if (newState) "Ligado" else "Desligado"
            subTextChild?.setTextColor(if (newState) Color.parseColor("#059669") else Color.parseColor("#64748B"))
            android.widget.Toast.makeText(
                context,
                if (newState) "🚦 Semáforo ATIVADO!" else "⏸️ Semáforo PAUSADO!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        togglesRow.addView(semaforoCard)

        // 3. METAS FINANCEIRAS CARD
        togglesRow.addView(createQuickCard("🎯", "Metas", "Financeiro", false) {
            launchAppScreen("FINANCIAL_TARGETS")
            dismissQuickSettingsModal()
        })

        root.addView(togglesRow)

        // Quando a câmera está gravando: exibe atalhos rápidos [ 🔄 Lente ] e [ 👁️ Ver imagem ]
        if (isCamRecording) {
            val camSubRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            }
            val curLens = com.creativeam.goorno.camera.SecretCameraRecordingService.currentLensState.value
            val btnLens = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = roundedCardBg("#F8FAFC", "#CBD5E1", 12f, 1)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                val tvL = TextView(context).apply {
                    text = "🔄 Lente ${curLens.lowercase().replaceFirstChar { it.uppercase() }}"
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                }
                addView(tvL)
                setOnClickListener {
                    com.creativeam.goorno.camera.SecretCameraRecordingService.switchLens(context)
                    val newL = if (com.creativeam.goorno.camera.SecretCameraRecordingService.currentLensState.value == "FRONTAL") "traseira" else "frontal"
                    tvL.text = "🔄 Lente $newL"
                    android.widget.Toast.makeText(context, "Trocando para lente $newL...", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            camSubRow.addView(btnLens)

            val btnView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = roundedCardBg("#F8FAFC", "#CBD5E1", 12f, 1)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6)
                }
                val tvV = TextView(context).apply {
                    text = "👁️ Gravações"
                    setTextColor(Color.parseColor("#0F2F28"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                }
                addView(tvV)
                setOnClickListener {
                    launchAppScreen("SECRET_RECORDINGS")
                    dismissQuickSettingsModal()
                }
            }
            camSubRow.addView(btnView)
            root.addView(camSubRow)
        }

        // "Última Oferta" Card
        val prefs = context.getSharedPreferences("goorno_persisted_notification", Context.MODE_PRIVATE)
        val hasRide = prefs.getBoolean("has_ride", false)
        val price = prefs.getFloat("price", 0f).toDouble()
        val sourceApp = prefs.getString("source_app", "99") ?: "99"
        val origAddress = prefs.getString("origin_address", "") ?: ""
        val destAddress = prefs.getString("dest_address", "") ?: ""
        val timestamp = prefs.getLong("timestamp", 0L)

        val timeStr = if (timestamp > 0) {
            java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(timestamp))
        } else {
            java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
        }

        val offerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedCardBg("#F8FAFC", "#E2E8F0", 16f, 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        // Top row inside Offer Card: Badge + Price + "Última oferta · HH:mm"
        val offerHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val is99Offer = sourceApp.contains("99", ignoreCase = true)
        val tvAppPill = TextView(context).apply {
            text = if (is99Offer) "99" else "Uber"
            setTextColor(if (is99Offer) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = if (is99Offer) roundedBg("#FACC15", 6f) else roundedBg("#000000", 6f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        }
        offerHeaderRow.addView(tvAppPill)

        val tvOfferPrice = TextView(context).apply {
            text = if (hasRide && price > 0) formatCurrency(price) else "R$ --"
            setTextColor(Color.parseColor("#0F2F28"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        offerHeaderRow.addView(tvOfferPrice)

        val tvOfferTime = TextView(context).apply {
            text = if (hasRide) "Última oferta · $timeStr" else "Aguardando oferta"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
        }
        offerHeaderRow.addView(tvOfferTime)
        offerCard.addView(offerHeaderRow)

        // Route section: EMBARQUE and DESTINO with Street View clicks
        val routeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val timelineCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }
        val ringEmbarque = TextView(context).apply {
            text = "○"
            setTextColor(Color.parseColor("#059669"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val lineTimeline = View(context).apply {
            background = roundedBg("#CBD5E1", 1f)
            layoutParams = LinearLayout.LayoutParams(dp(2), dp(22))
        }
        val sqDestino = TextView(context).apply {
            text = "■"
            setTextColor(Color.parseColor("#0F2F28"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }
        timelineCol.addView(ringEmbarque)
        timelineCol.addView(lineTimeline)
        timelineCol.addView(sqDestino)
        routeContainer.addView(timelineCol)

        val addrCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Embarque Row
        val embarqueRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                if (origAddress.isNotBlank()) {
                    val intent = Intent(context, com.creativeam.goorno.activity.StreetViewTrampolineActivity::class.java).apply {
                        putExtra("EXTRA_ADDRESS", origAddress)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    dismissQuickSettingsModal()
                } else {
                    android.widget.Toast.makeText(context, "Endereço de embarque não disponível.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        val embarqueTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLblEmbarque = TextView(context).apply {
            text = "EMBARQUE"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvValEmbarque = TextView(context).apply {
            text = if (origAddress.isNotBlank()) origAddress else "Toque para abrir Street View"
            setTextColor(Color.parseColor("#1E293B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        embarqueTextCol.addView(tvLblEmbarque)
        embarqueTextCol.addView(tvValEmbarque)
        embarqueRow.addView(embarqueTextCol)

        val tvArrow1 = TextView(context).apply {
            text = "›"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }
        embarqueRow.addView(tvArrow1)
        addrCol.addView(embarqueRow)

        val divLine = View(context).apply {
            background = roundedBg("#E2E8F0", 1f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
        addrCol.addView(divLine)

        // Destino Row
        val destinoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                if (destAddress.isNotBlank()) {
                    val intent = Intent(context, com.creativeam.goorno.activity.StreetViewTrampolineActivity::class.java).apply {
                        putExtra("EXTRA_ADDRESS", destAddress)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    dismissQuickSettingsModal()
                } else {
                    android.widget.Toast.makeText(context, "Endereço de destino não disponível.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        val destinoTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvLblDestino = TextView(context).apply {
            text = "DESTINO"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvValDestino = TextView(context).apply {
            text = if (destAddress.isNotBlank()) destAddress else "Toque para abrir Street View"
            setTextColor(Color.parseColor("#1E293B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        destinoTextCol.addView(tvLblDestino)
        destinoTextCol.addView(tvValDestino)
        destinoRow.addView(destinoTextCol)

        val tvArrow2 = TextView(context).apply {
            text = "›"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }
        destinoRow.addView(tvArrow2)
        addrCol.addView(destinoRow)

        routeContainer.addView(addrCol)
        offerCard.addView(routeContainer)

        // Botão de Confirmação Rápida: "Aceitou?"
        if (hasRide && price > 0.0) {
            var isAccepted = prefs.getBoolean("last_ride_accepted", false)
            val offerId = prefs.getString("offer_id", "") ?: ""

            val acceptContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = if (isAccepted) roundedCardBg("#ECFDF5", "#10B981", 12f, 1) else roundedCardBg("#F1F5F9", "#CBD5E1", 12f, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            }

            val tvStatus = TextView(context).apply {
                text = if (isAccepted) "✓ Viagem Aceita!" else "Aceitou a corrida?"
                setTextColor(if (isAccepted) Color.parseColor("#059669") else Color.parseColor("#334155"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            acceptContainer.addView(tvStatus)

            lateinit var btnAction: TextView
            btnAction = TextView(context).apply {
                text = if (isAccepted) "Desfazer" else "✓ Sim, Aceitei"
                setTextColor(if (isAccepted) Color.parseColor("#DC2626") else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = if (isAccepted) roundedBg("#FEE2E2", 8f) else roundedBg("#16A34A", 8f)
                setOnClickListener {
                    val app = context.applicationContext as? GoOrNoApplication
                    val db = app?.database
                    if (!isAccepted) {
                        isAccepted = true
                        prefs.edit().putBoolean("last_ride_accepted", true).apply()
                        if (db != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    if (offerId.isNotBlank()) {
                                        db.rideOfferDao().updateActualReceivedPrice(offerId, price)
                                    } else {
                                        val recent = db.rideOfferDao().getAllRealOffersOnce().firstOrNull()
                                        if (recent != null) {
                                            db.rideOfferDao().updateActualReceivedPrice(recent.id, price)
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                        CopilotNotificationReceiver.showActiveNotification(context)
                        android.widget.Toast.makeText(context, "✓ Corrida de ${formatCurrency(price)} marcada como ACEITA!", android.widget.Toast.LENGTH_SHORT).show()
                        tvStatus.text = "✓ Viagem Aceita!"
                        tvStatus.setTextColor(Color.parseColor("#059669"))
                        btnAction.text = "Desfazer"
                        btnAction.setTextColor(Color.parseColor("#DC2626"))
                        btnAction.background = roundedBg("#FEE2E2", 8f)
                        acceptContainer.background = roundedCardBg("#ECFDF5", "#10B981", 12f, 1)
                    } else {
                        isAccepted = false
                        prefs.edit().putBoolean("last_ride_accepted", false).apply()
                        if (db != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    if (offerId.isNotBlank()) {
                                        db.rideOfferDao().updateActualReceivedPrice(offerId, null)
                                    } else {
                                        val recent = db.rideOfferDao().getAllRealOffersOnce().firstOrNull()
                                        if (recent != null) {
                                            db.rideOfferDao().updateActualReceivedPrice(recent.id, null)
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                        CopilotNotificationReceiver.showActiveNotification(context)
                        android.widget.Toast.makeText(context, "Corrida desmarcada (voltará para Recusadas).", android.widget.Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Aceitou a corrida?"
                        tvStatus.setTextColor(Color.parseColor("#334155"))
                        btnAction.text = "✓ Sim, Aceitei"
                        btnAction.setTextColor(Color.WHITE)
                        btnAction.background = roundedBg("#16A34A", 8f)
                        acceptContainer.background = roundedCardBg("#F1F5F9", "#CBD5E1", 12f, 1)
                    }
                }
            }
            acceptContainer.addView(btnAction)
            offerCard.addView(acceptContainer)
        }

        root.addView(offerCard)

        // Icon Actions Row: [🕒 Corridas] [▶️ Gravações] [⏱️ Jornadas] [🏠 Abrir app]
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        fun createCircularAction(icon: String, label: String, isHome: Boolean = false, onClick: () -> Unit): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onClick() }

                val iconBtn = TextView(context).apply {
                    text = icon
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(if (isHome) Color.WHITE else Color.parseColor("#0F2F28"))
                    gravity = Gravity.CENTER
                    background = if (isHome) roundedBg("#0F2F28", 22f) else roundedBg("#F1F5F9", 22f)
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                }
                addView(iconBtn)

                val tvLbl = TextView(context).apply {
                    text = label
                    setTextColor(Color.parseColor("#64748B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                }
                addView(tvLbl)
            }
        }

        actionsRow.addView(createCircularAction("📋", "Corridas") {
            launchAppScreen("HISTORY")
            dismissQuickSettingsModal()
        })
        actionsRow.addView(createCircularAction("▶️", "Gravações") {
            launchAppScreen("SECRET_RECORDINGS")
            dismissQuickSettingsModal()
        })
        actionsRow.addView(createCircularAction("🎯", "Metas") {
            launchAppScreen("FINANCIAL_TARGETS")
            dismissQuickSettingsModal()
        })
        actionsRow.addView(createCircularAction("🏠", "Abrir app", isHome = true) {
            launchAppScreen("HOME")
            dismissQuickSettingsModal()
        })
        root.addView(actionsRow)

        // Bottom Pill: [ ⏻ ] Desativar o copiloto
        val btnDisable = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBg("#F1F5F9", 14f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                dismissQuickSettingsModal()
                onUserRequestedDisableCopilot?.invoke()
                try {
                    val disableIntent = Intent(context, com.creativeam.goorno.service.CopilotNotificationReceiver::class.java).apply {
                        action = com.creativeam.goorno.service.CopilotNotificationReceiver.ACTION_DISABLE_COPILOT
                    }
                    context.sendBroadcast(disableIntent)
                } catch (_: Throwable) {}
            }

            val tvPowerIcon = TextView(context).apply {
                text = "⏻"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 0, dp(10), 0)
            }
            addView(tvPowerIcon)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvDisableTitle = TextView(context).apply {
                text = "Desativar o copiloto"
                setTextColor(Color.parseColor("#0F2F28"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(typeface, Typeface.BOLD)
            }
            val tvDisableSub = TextView(context).apply {
                text = "Desliga tudo e oculta a bolha"
                setTextColor(Color.parseColor("#64748B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            }
            textCol.addView(tvDisableTitle)
            textCol.addView(tvDisableSub)
            addView(textCol)
        }
        root.addView(btnDisable)

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

    private fun getOverlayTypeface(fontFamilyPref: String): Typeface {
        return Typeface.DEFAULT_BOLD
    }

    private fun roundedBg(hexColor: String, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun roundedCardWithThickBottomBg(
        bgHex: String,
        strokeHex: String,
        radiusDp: Float = 18f,
        strokeDp: Int = 2,
        bottomLipDp: Int = 6
    ): Drawable {
        val outerRadius = dp(radiusDp.toInt()).toFloat()
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = outerRadius
            setColor(Color.parseColor(strokeHex))
        }

        val innerRadius = Math.max(0f, outerRadius - dp(strokeDp))
        val innerBottomRadius = Math.max(0f, outerRadius - dp(bottomLipDp))
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                innerRadius, innerRadius,
                innerRadius, innerRadius,
                innerBottomRadius, innerBottomRadius,
                innerBottomRadius, innerBottomRadius
            )
            setColor(Color.parseColor(bgHex))
        }

        return LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerInset(
                1,
                dp(strokeDp),
                dp(strokeDp),
                dp(strokeDp),
                dp(bottomLipDp)
            )
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

    private fun roundedRightBg(hexColor: String, radiusDp: Float): GradientDrawable {
        val r = dp(radiusDp.toInt()).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            setColor(Color.parseColor(hexColor))
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
