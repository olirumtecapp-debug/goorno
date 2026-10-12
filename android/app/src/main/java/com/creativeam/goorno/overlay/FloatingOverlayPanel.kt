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
import android.text.TextUtils
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
import com.creativeam.goorno.model.TrafficLightVerdict
import java.util.Locale
import kotlin.math.abs

/**
 * Painel Flutuante Nativo v2.6.0 — Zero Flutuação em Espera + Fechamento 1-Toque Instantâneo.
 *
 * - Enquanto o motorista está trabalhando e aguardando corrida, NADA fica flutuando na tela!
 *   O status de ativo fica exclusivamente no ícone da barra superior do Android (ao lado do relógio e do ícone da 99),
 *   onde há o botão "Desativar GoOrNo".
 * - Quando toca corrida (ou o motorista abre a lista de Solicitações / clica no mapinha da 99),
 *   a janela de cálculo abre automaticamente, mostra o Semáforo + R$/km + R$/h + Lucro Líquido,
 *   conta os segundos regressivamente e:
 *   1. Fecha sozinha ao fim da contagem (removendo totalmente a janela da tela).
 *   2. Fecha IMEDIATAMENTE com 1 simples toque em QUALQUER lugar do card (ou no botão grande "✕ Fechar").
 *   3. Nunca reabre sozinha para a mesma corrida já exibida/fechada!
 */
class FloatingOverlayPanel(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootContainer: LinearLayout? = null
    private var cardContainer: LinearLayout? = null
    private var offersListLayout: LinearLayout? = null
    private var tvHeaderTitle: TextView? = null
    private var tvTimerHint: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false
    private var isAccessibilityActive = false
    private var lastResultsShown: List<FinancialEvaluationResult> = emptyList()

    private var currentOpacityPercent: Int = 94
    private var displayDurationSec: Int = 8
    private var remainingSeconds: Int = 8
    private var isMonitoringEnabled: Boolean = true
    var onUserRequestedDisableCopilot: (() -> Unit)? = null
    var onOfferCardClosed: (() -> Unit)? = null

    // Contagem regressiva de 1 em 1 segundo que remove completamente a janela ao terminar
    private val countdownRunnable = object : Runnable {
        override fun run() {
            remainingSeconds--
            if (remainingSeconds <= 0) {
                hidePanel()
                onOfferCardClosed?.invoke()
            } else {
                tvTimerHint?.text = "⏱ ${remainingSeconds}s"
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

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

    fun isCardCurrentlyVisible(): Boolean = isAdded && cardContainer?.visibility == View.VISIBLE

    fun applyConfig(config: DriverFinancialConfig) {
        mainHandler.post {
            currentOpacityPercent = config.overlayOpacityPercent.coerceIn(25, 100)
            displayDurationSec = config.overlayAutoMinimizeSec.coerceIn(3, 45)
            isMonitoringEnabled = config.copilotMonitoringEnabled
            rootContainer?.alpha = currentOpacityPercent / 100f
            if (!isMonitoringEnabled) {
                hidePanel()
            }
        }
    }

    /**
     * Atualiza apenas o estado interno de acessibilidade — NUNCA desenha barrinha flutuante em espera,
     * deixando a tela do motorista 100% limpa enquanto trabalha!
     */
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
     * Exibe 1 ou mais viagens simultâneas diretamente na janela flutuante e agenda o fechamento total automático.
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

            lastResultsShown = results
            ensureViewsCreated()
            attachWindowIfNeeded()

            rootContainer?.alpha = currentOpacityPercent / 100f
            cardContainer?.visibility = View.VISIBLE
            offersListLayout?.removeAllViews()

            val primary = results.first()
            val (primaryBadge, _, _) = verdictPalette(primary.verdict)
            cardContainer?.background = roundedCardBg("#FFFFFF", primaryBadge, 20f, 2)

            val appName = primary.offer.sourceApp.displayName
            val simTag = if (primary.offer.isSimulated) " · TESTE" else ""
            tvHeaderTitle?.text = if (results.size > 1) {
                "$appName (${results.size} Ofertas)$simTag"
            } else {
                "$appName (${primary.offer.rideCategory ?: "Oferta"})$simTag"
            }

            remainingSeconds = displayDurationSec
            tvTimerHint?.text = "⏱ ${remainingSeconds}s"

            results.take(3).forEachIndexed { index, eval ->
                val itemView = buildOfferItemView(
                    index = index + 1,
                    totalCount = results.size,
                    result = eval
                )
                offersListLayout?.addView(itemView)
            }

            // Inicia contagem regressiva de 1 em 1 segundo para fechar e remover totalmente a janela da tela
            mainHandler.removeCallbacks(countdownRunnable)
            mainHandler.postDelayed(countdownRunnable, 1000L)
        }
    }

    /**
     * Acionado quando o motorista toca no card ou no botão "✕ Fechar" para fechar a janela imediatamente.
     */
    fun dismissCurrentOfferCardByUser() {
        hidePanel()
        onOfferCardClosed?.invoke()
    }

    fun hideOfferCardOnly() {
        hidePanel()
        onOfferCardClosed?.invoke()
    }

    /**
     * Remove completamente a janela flutuante da tela (deixando a tela 100% livre).
     */
    fun hidePanel() {
        mainHandler.post {
            mainHandler.removeCallbacks(countdownRunnable)
            cardContainer?.visibility = View.GONE
            if (isAdded && rootContainer != null) {
                try {
                    windowManager.removeView(rootContainer)
                } catch (_: Exception) {
                }
                isAdded = false
                RuntimeDiagnosticsBus.setOverlayVisible(false)
            }
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

    private fun buildOfferItemView(
        index: Int,
        totalCount: Int,
        result: FinancialEvaluationResult
    ): View {
        val offer = result.offer
        val (badgeHex, softBgHex, borderHex) = verdictPalette(result.verdict)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedCardBg(softBgHex, borderHex, 14f, 1)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 1) lp.topMargin = dp(8)
            layoutParams = lp
            // Tocar em qualquer área do card fecha a janela imediatamente sem esforço!
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        val priceStr = offer.offeredPrice?.let { formatCurrency(it) } ?: "R$ --"
        val distStr = result.totalConsideredDistanceKm?.let {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
        } ?: "-- km"
        val minStr = result.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"
        val grossKmStr = result.grossPerKm?.let { formatCurrency(it) + "/km" } ?: "--/km"
        val grossHrStr = result.grossPerHour?.let { formatCurrency(it) + "/h" } ?: "--/h"
        val netStr = result.estimatedNetProfit?.let { formatCurrency(it) } ?: "--"

        // Faixa Pílula Moderna do Semáforo + Valor da Oferta
        val tvBadge = TextView(context).apply {
            val prefix = if (totalCount > 1) "#$index " else ""
            text = "$prefix${result.verdict.label}  ·  $priceStr  ($distStr · $minStr)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBg(badgeHex, 10f)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        box.addView(tvBadge)

        // Linha Principal Clean de Alto Contraste: R$/km | R$/hora | Lucro Líquido
        val tvMetrics = TextView(context).apply {
            text = "$grossKmStr   •   $grossHrStr   •   Lucro: $netStr"
            setTextColor(Color.parseColor("#073B32"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }
        box.addView(tvMetrics)

        // Detalhe de Embarque + Viagem quando for 1 oferta
        if (totalCount == 1) {
            val costStr = result.estimatedTotalCost?.let { formatCurrency(it) } ?: "--"
            val tvSub = TextView(context).apply {
                val pKm = offer.pickupDistanceKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it) }
                val tKm = offer.tripDistanceKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it) }
                val routeInfo = if (pKm != null && tKm != null) "Embarque: $pKm + Viagem: $tKm  ·  " else ""
                text = "${routeInfo}Custo: $costStr"
                setTextColor(Color.parseColor("#334155"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(2)
                layoutParams = lp
                setOnClickListener { dismissCurrentOfferCardByUser() }
            }
            box.addView(tvSub)
        }

        // Botões Pílula Modernos 1-Toque para abrir Origem e Destino no Google Street View / Maps
        if (!offer.originAddress.isNullOrBlank() || !offer.destinationAddress.isNullOrBlank()) {
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(7)
                layoutParams = lp
            }

            if (!offer.originAddress.isNullOrBlank()) {
                val btnOrig = TextView(context).apply {
                    text = "📍 Origem: ${offer.originAddress.take(18)}"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 1
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = roundedBg("#073B32", 20f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(5)
                    }
                    setOnClickListener { launchStreetViewOrMap(offer.originAddress) }
                }
                btnRow.addView(btnOrig)
            }

            if (!offer.destinationAddress.isNullOrBlank()) {
                val btnDest = TextView(context).apply {
                    text = "🏁 Destino: ${offer.destinationAddress.take(18)}"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 1
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = roundedBg("#059669", 20f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { launchStreetViewOrMap(offer.destinationAddress) }
                }
                btnRow.addView(btnDest)
            }

            box.addView(btnRow)
        }

        return box
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
            x = 0
            y = dp(18)
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = currentOpacityPercent / 100f
            // Essencial: impede que a janela do GoOrNo apareça na árvore de Acessibilidade
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        // CARD DE ANÁLISE DE OFERTA (Só existe na tela enquanto está mostrando o cálculo!)
        cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedCardBg("#FFFFFF", "#059669", 20f, 2)
            minimumWidth = dp(330)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        tvHeaderTitle = TextView(context).apply {
            text = "GoOrNo Copiloto"
            setTextColor(Color.parseColor("#073B32"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        tvTimerHint = TextView(context).apply {
            text = "⏱ 8s"
            setTextColor(Color.parseColor("#073B32"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBg("#E2E8F0", 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        // Botão grande e fácil de tocar "✕ Fechar"
        val btnClose = TextView(context).apply {
            text = "✕ Fechar"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = roundedBg("#DC2626", 14f)
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        topRow.addView(tvHeaderTitle)
        topRow.addView(tvTimerHint)
        topRow.addView(btnClose)

        offersListLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        val tvTapAnywhereHint = TextView(context).apply {
            text = "👆 Toque em qualquer parte da janela para fechar"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
            setOnClickListener { dismissCurrentOfferCardByUser() }
        }

        cardContainer?.addView(topRow)
        cardContainer?.addView(offersListLayout)
        cardContainer?.addView(tvTapAnywhereHint)
        rootContainer?.addView(cardContainer)

        // Permite arrastar o card caso queira mover, e se for um toque rápido (click) fecha na hora!
        val dragOrTapListener = object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var wasDragged = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return false
                when (event.action) {
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
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            wasDragged = true
                            lp.x = initialX + dx
                            lp.y = initialY + dy
                            if (isAdded && rootContainer != null) {
                                try {
                                    windowManager.updateViewLayout(rootContainer, lp)
                                } catch (_: Exception) {
                                }
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!wasDragged) {
                            dismissCurrentOfferCardByUser()
                        }
                        return true
                    }
                }
                return false
            }
        }

        cardContainer?.setOnTouchListener(dragOrTapListener)
    }

    private fun verdictPalette(verdict: TrafficLightVerdict): Triple<String, String, String> {
        return when (verdict) {
            TrafficLightVerdict.GREEN -> Triple("#059669", "#F0FDF4", "#86EFAC")
            TrafficLightVerdict.YELLOW -> Triple("#D97706", "#FFFBEB", "#FCD34D")
            TrafficLightVerdict.RED -> Triple("#DC2626", "#FEF2F2", "#FCA5A5")
            TrafficLightVerdict.GRAY_NEUTRAL -> Triple("#475569", "#F8FAFC", "#CBD5E1")
        }
    }

    private fun launchStreetViewOrMap(address: String) {
        try {
            val encoded = Uri.encode(address)
            val mapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun formatCurrency(value: Double): String =
        String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value)

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
