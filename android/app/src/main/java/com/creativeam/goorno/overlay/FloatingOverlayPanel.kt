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

/**
 * Painel Flutuante Nativo Direto v2.2.0 — Visual Clean, Claro e Moderno.
 * - Sem botões de expandir/minimizar: abre DIRETO com todas as informações assim que a corrida toca!
 * - Some automaticamente após o tempo configurado pelo motorista (ex: 5s, 8s, 10s, 12s, 15s).
 * - Suporta leitura e exibição de 1 ou MÚLTIPLAS viagens simultâneas na tela (Lista de Ofertas da 99 / Radar Uber).
 * - Permite arrastar livremente pela tela e abrir Origem/Destino no Google Street View em 1 toque com botões modernos.
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

    private var currentOpacityPercent: Int = 92
    private var displayDurationSec: Int = 10

    // Quando o tempo definido pelo motorista termina, a janelinha some sozinha!
    private val autoHideRunnable = Runnable {
        hidePanel()
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun applyConfig(config: DriverFinancialConfig) {
        mainHandler.post {
            currentOpacityPercent = config.overlayOpacityPercent.coerceIn(25, 100)
            displayDurationSec = config.overlayAutoMinimizeSec.coerceIn(3, 60)
            rootContainer?.alpha = currentOpacityPercent / 100f
        }
    }

    fun showEvaluation(result: FinancialEvaluationResult) {
        showEvaluations(listOf(result))
    }

    /**
     * Exibe 1 ou mais viagens simultâneas diretamente na janela flutuante clean e agenda o desaparecimento automático.
     */
    fun showEvaluations(results: List<FinancialEvaluationResult>) {
        if (results.isEmpty()) return
        mainHandler.post {
            if (!hasOverlayPermission()) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.PERMISSION_MISSING)
                return@post
            }
            ensureViewsCreated()
            if (!isAdded) {
                try {
                    windowManager.addView(rootContainer, layoutParams)
                    isAdded = true
                    RuntimeDiagnosticsBus.setOverlayVisible(true)
                } catch (_: Exception) {
                    return@post
                }
            }

            rootContainer?.alpha = currentOpacityPercent / 100f
            offersListLayout?.removeAllViews()

            val primary = results.first()
            val (primaryBadge, _, _) = verdictPalette(primary.verdict)
            // Card externo branco clean com borda da cor do semáforo
            cardContainer?.background = roundedCardBg("#FFFFFF", primaryBadge, 20f, 2)

            val appName = primary.offer.sourceApp.displayName
            val simTag = if (primary.offer.isSimulated) " · TESTE" else ""
            tvHeaderTitle?.text = if (results.size > 1) {
                "GoOrNo · $appName (${results.size} Viagens)$simTag"
            } else {
                "GoOrNo · $appName (${primary.offer.rideCategory ?: "Oferta"})$simTag"
            }
            tvTimerHint?.text = "⏱ ${displayDurationSec}s"

            results.take(3).forEachIndexed { index, eval ->
                val itemView = buildOfferItemView(
                    index = index + 1,
                    totalCount = results.size,
                    result = eval
                )
                offersListLayout?.addView(itemView)
            }

            // Reinicia o cronômetro para sumir sozinho após o tempo configurado pelo motorista
            mainHandler.removeCallbacks(autoHideRunnable)
            mainHandler.postDelayed(autoHideRunnable, displayDurationSec * 1000L)
        }
    }

    fun hidePanel() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
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
            }
            box.addView(tvSub)
        }

        // Botões Pílula Modernos 1-Toque para abrir Origem e Destino no Google Street View
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(46)
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            alpha = currentOpacityPercent / 100f
        }

        cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = roundedCardBg("#FFFFFF", "#059669", 20f, 2)
            minimumWidth = dp(328)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
        }

        // Topo Clean: Título + Pílula do Timer + Botão Fechar Moderno
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvHeaderTitle = TextView(context).apply {
            text = "GoOrNo Copiloto"
            setTextColor(Color.parseColor("#073B32"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvTimerHint = TextView(context).apply {
            text = "⏱ 10s"
            setTextColor(Color.parseColor("#073B32"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBg("#E2E8F0", 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#DC2626"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBg("#FEE2E2", 12f)
            setOnClickListener { hidePanel() }
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

        cardContainer?.addView(topRow)
        cardContainer?.addView(offersListLayout)
        rootContainer?.addView(cardContainer)

        // Permite arrastar a janelinha livremente para qualquer posição da tela
        val dragListener = object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        if (isAdded && rootContainer != null) {
                            try {
                                windowManager.updateViewLayout(rootContainer, lp)
                            } catch (_: Exception) {
                            }
                        }
                        return true
                    }
                }
                return false
            }
        }

        cardContainer?.setOnTouchListener(dragListener)
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
