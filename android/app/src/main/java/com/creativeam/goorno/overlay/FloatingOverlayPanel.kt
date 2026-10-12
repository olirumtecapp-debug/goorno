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
 * Painel Flutuante Nativo (WindowManager TYPE_APPLICATION_OVERLAY).
 * Compacto, reposicionável (arrastável), com controle de transparência (25% a 100%),
 * minimizar/expandir/fechar e abertura em 1 toque da Origem e Destino no Google Street View / Maps.
 * Jamais realiza cliques automáticos na tela da Uber ou 99.
 */
class FloatingOverlayPanel(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootContainer: LinearLayout? = null
    private var collapsedPill: LinearLayout? = null
    private var expandedCard: LinearLayout? = null

    private var tvPillStatus: TextView? = null
    private var tvHeaderSource: TextView? = null
    private var tvVerdictBadge: TextView? = null
    private var tvPriceMain: TextView? = null
    private var tvGrossMetrics: TextView? = null
    private var tvNetMetrics: TextView? = null
    private var tvCostDetails: TextView? = null
    private var tvReasonsAndWarnings: TextView? = null
    private var btnStreetOrigin: TextView? = null
    private var btnStreetDestination: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false
    private var isMinimized = false

    private var currentOpacityPercent: Int = 90
    private var autoMinimizeSec: Int = 12
    private var currentOriginAddress: String? = null
    private var currentDestinationAddress: String? = null

    private val autoMinimizeRunnable = Runnable {
        setMinimized(true)
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun applyConfig(config: DriverFinancialConfig) {
        mainHandler.post {
            currentOpacityPercent = config.overlayOpacityPercent.coerceIn(25, 100)
            autoMinimizeSec = config.overlayAutoMinimizeSec.coerceIn(5, 60)
            rootContainer?.alpha = currentOpacityPercent / 100f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showStandbyPanel() {
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
                }
            }
            setMinimized(true)
            tvPillStatus?.text = "GoOrNo · Monitorando Uber/99"
        }
    }

    fun showEvaluation(result: FinancialEvaluationResult) {
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

            val offer = result.offer
            currentOriginAddress = offer.originAddress
            currentDestinationAddress = offer.destinationAddress

            val (verdictColor, borderColor) = when (result.verdict) {
                TrafficLightVerdict.GREEN -> Pair("#0E9F6E", "#31C48D")
                TrafficLightVerdict.YELLOW -> Pair("#D97706", "#FBBF24")
                TrafficLightVerdict.RED -> Pair("#DC2626", "#F87171")
                TrafficLightVerdict.GRAY_NEUTRAL -> Pair("#4B5563", "#9CA3AF")
            }

            val simTag = if (offer.isSimulated) " [SIMULAÇÃO]" else ""
            val categorySuffix = offer.rideCategory?.let { " · $it" } ?: ""
            tvHeaderSource?.text = "${offer.sourceApp.displayName}$categorySuffix$simTag"

            tvVerdictBadge?.text = result.verdict.label
            tvVerdictBadge?.background = roundedBg(verdictColor, 8f)
            expandedCard?.background = roundedCardBg("#04241E", borderColor, 16f, 2)
            collapsedPill?.background = roundedCardBg("#073B32", borderColor, 24f, 2)

            val priceStr = offer.offeredPrice?.let { formatCurrency(it) } ?: "R$ --"
            val distStr = result.totalConsideredDistanceKm?.let {
                String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
            } ?: "-- km"
            val minStr = result.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"

            tvPriceMain?.text = "Oferta: $priceStr  |  $distStr  |  $minStr"

            val grossKmStr = result.grossPerKm?.let { formatCurrency(it) + "/km" } ?: "--/km"
            val grossHrStr = result.grossPerHour?.let { formatCurrency(it) + "/h" } ?: "--/h"
            tvGrossMetrics?.text = "Bruto: $grossKmStr   ·   $grossHrStr"

            val netStr = result.estimatedNetProfit?.let { formatCurrency(it) } ?: "--"
            val netKmStr = result.netProfitPerKm?.let { formatCurrency(it) + "/km" } ?: "--/km"
            val netHrStr = result.netProfitPerHour?.let { formatCurrency(it) + "/h" } ?: "--/h"
            tvNetMetrics?.text = "Lucro Líq. Est.: $netStr ($netKmStr · $netHrStr)"

            val costStr = result.estimatedTotalCost?.let { formatCurrency(it) } ?: "--"
            val partialFlag = if (!result.isAnalysisComplete) "  ⚠️ LEITURA PARCIAL/INCOMPLETA" else ""
            tvCostDetails?.text = "Custo Operacional Est.: $costStr$partialFlag"

            val summaryLines = mutableListOf<String>()
            summaryLines.addAll(result.reasons.take(2))
            summaryLines.addAll(result.warnings.take(2))
            tvReasonsAndWarnings?.text = if (summaryLines.isNotEmpty()) {
                summaryLines.joinToString("\n")
            } else {
                result.verdict.shortAction
            }

            // Atualiza botões de Street View (Origem e Destino)
            btnStreetOrigin?.apply {
                if (!currentOriginAddress.isNullOrBlank()) {
                    visibility = View.VISIBLE
                    text = "📍 Origem (360°): ${currentOriginAddress!!.take(22)}"
                } else {
                    visibility = View.GONE
                }
            }

            btnStreetDestination?.apply {
                if (!currentDestinationAddress.isNullOrBlank()) {
                    visibility = View.VISIBLE
                    text = "🏁 Destino (360°): ${currentDestinationAddress!!.take(22)}"
                } else {
                    visibility = View.GONE
                }
            }

            tvPillStatus?.text = "${offer.sourceApp.displayName}: $grossKmStr · $grossHrStr"

            setMinimized(false)
            mainHandler.removeCallbacks(autoMinimizeRunnable)
            mainHandler.postDelayed(autoMinimizeRunnable, autoMinimizeSec * 1000L)
        }
    }

    fun hidePanel() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoMinimizeRunnable)
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

    private fun setMinimized(minimized: Boolean) {
        isMinimized = minimized
        collapsedPill?.visibility = if (minimized) View.VISIBLE else View.GONE
        expandedCard?.visibility = if (minimized) View.GONE else View.VISIBLE
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
            y = dp(70)
        }

        rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            alpha = currentOpacityPercent / 100f
        }

        // 1. PÍLULA MINIMIZADA
        collapsedPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedCardBg("#073B32", "#97B304", 24f, 2)
        }

        tvPillStatus = TextView(context).apply {
            text = "GoOrNo Ativo"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val tvExpandBtn = TextView(context).apply {
            text = "  Expandir"
            setTextColor(Color.parseColor("#B8D906"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { setMinimized(false) }
        }

        collapsedPill?.addView(tvPillStatus)
        collapsedPill?.addView(tvExpandBtn)

        // 2. CARD EXPANDIDO COMPLETO
        expandedCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedCardBg("#04241E", "#97B304", 16f, 2)
            minimumWidth = dp(310)
        }

        // Linha Superior: Origem + Minimizar + Fechar
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvHeaderSource = TextView(context).apply {
            text = "Uber Driver"
            setTextColor(Color.parseColor("#B8D906"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMin = TextView(context).apply {
            text = "Minimizar"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBg("#073B32", 6f)
            setOnClickListener { setMinimized(true) }
        }

        val btnClose = TextView(context).apply {
            text = " ✕ "
            setTextColor(Color.parseColor("#FCA5A5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(4), dp(2))
            setOnClickListener { hidePanel() }
        }

        topRow.addView(tvHeaderSource)
        topRow.addView(btnMin)
        topRow.addView(btnClose)

        tvVerdictBadge = TextView(context).apply {
            text = "VERDE — COMPENSA"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg("#0E9F6E", 8f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
        }

        tvPriceMain = TextView(context).apply {
            text = "Oferta: R$ --"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
        }

        tvGrossMetrics = TextView(context).apply {
            text = "Bruto: R$ --/km · R$ --/h"
            setTextColor(Color.parseColor("#A7F3D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(3)
            layoutParams = lp
        }

        tvNetMetrics = TextView(context).apply {
            text = "Lucro Líq. Est.: R$ --"
            setTextColor(Color.parseColor("#B8D906"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(2)
            layoutParams = lp
        }

        tvCostDetails = TextView(context).apply {
            text = "Custo Est.: R$ --"
            setTextColor(Color.parseColor("#D1FAE5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(2)
            layoutParams = lp
        }

        tvReasonsAndWarnings = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#E5E7EB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(4)
            layoutParams = lp
        }

        // Botões 1-Toque para abrir Origem e Destino no Google Street View / Mapa
        btnStreetOrigin = TextView(context).apply {
            text = "📍 Ver Origem no Street View"
            setTextColor(Color.parseColor("#04241E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBg("#97B304", 8f)
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            layoutParams = lp
            setOnClickListener {
                currentOriginAddress?.let { addr -> launchStreetViewOrMap(addr) }
            }
        }

        btnStreetDestination = TextView(context).apply {
            text = "🏁 Ver Destino no Street View"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBg("#0D4A3E", 8f)
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(4)
            layoutParams = lp
            setOnClickListener {
                currentDestinationAddress?.let { addr -> launchStreetViewOrMap(addr) }
            }
        }

        expandedCard?.apply {
            addView(topRow)
            addView(tvVerdictBadge)
            addView(tvPriceMain)
            addView(tvGrossMetrics)
            addView(tvNetMetrics)
            addView(tvCostDetails)
            addView(tvReasonsAndWarnings)
            addView(btnStreetOrigin)
            addView(btnStreetDestination)
        }

        rootContainer?.addView(collapsedPill)
        rootContainer?.addView(expandedCard)

        // Suporte a arrastar e reposicionar livremente o painel na tela
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
                    MotionEvent.ACTION_UP -> {
                        val moved = abs(event.rawX - initialTouchX) > dp(8) || abs(event.rawY - initialTouchY) > dp(8)
                        if (!moved && isMinimized) {
                            setMinimized(false)
                        }
                        return true
                    }
                }
                return false
            }
        }

        collapsedPill?.setOnTouchListener(dragListener)
        expandedCard?.setOnTouchListener(dragListener)
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
