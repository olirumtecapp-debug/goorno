package com.creativeam.goorno.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.MainActivity
import com.creativeam.goorno.R
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.TrafficLightVerdict
import com.creativeam.goorno.ocr.OcrScreenCaptureService
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.util.StreetViewLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Receptor e Gerenciador Persistente da Notificação Superior do GoOrNo (v2.9.0).
 *
 * Melhorias v2.9.0:
 * - PERSISTÊNCIA TOTAL DOS DADOS DA CORRIDA NA NOTIFICAÇÃO:
 *   Salva todos os campos da corrida em SharedPreferences ("goorno_persisted_notification") e faz
 *   fusão inteligente (Smart Merge): se uma segunda atualização rápida da tela vier sem endereço
 *   de Origem, sem Destino ou sem minutos, NUNCA apaga os dados completos que já estavam na notificação!
 * - Botão 1: "📍 Origem 360°" -> Abre direto o Street View 360° da rua de embarque!
 * - Botão 2: "🏁 Destino 360°" -> Abre direto o Street View 360° da rua de destino!
 * - Botão 3: "🔴 Desativar" -> Desliga o GoOrNo quando o motorista parar de rodar!
 */
class CopilotNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? GoOrNoApplication

        when (action) {
            ACTION_DISABLE_COPILOT -> {
                app?.overlayPanel?.setMonitoringEnabled(false, accessibilityEnabled = false)
                app?.overlayPanel?.hidePanel()
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)

                cancelActiveNotification(context)
                try {
                    context.stopService(Intent(context, OcrScreenCaptureService::class.java))
                } catch (_: Exception) {
                }

                if (app != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            val currentConfig = app.preferencesRepository.getConfigOnce()
                            app.preferencesRepository.saveConfig(
                                currentConfig.copy(copilotMonitoringEnabled = false)
                            )
                        } catch (_: Exception) {
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                Toast.makeText(
                    context,
                    "🔴 GoOrNo desativado! Abra o app GoOrNo quando quiser ligar novamente.",
                    Toast.LENGTH_LONG
                ).show()
            }

            ACTION_OPEN_ORIGIN_STREET_VIEW -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
                if (address.isNotBlank()) {
                    StreetViewLauncher.openDirectStreetView(context, address)
                } else {
                    Toast.makeText(context, "Endereço de origem não disponível nesta oferta.", Toast.LENGTH_SHORT).show()
                }
            }

            ACTION_OPEN_DEST_STREET_VIEW -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
                if (address.isNotBlank()) {
                    StreetViewLauncher.openDirectStreetView(context, address)
                } else {
                    Toast.makeText(context, "Endereço de destino não disponível nesta oferta.", Toast.LENGTH_SHORT).show()
                }
            }

            ACTION_DISMISS_OVERLAY_CARD -> {
                app?.overlayPanel?.dismissCurrentOfferCardByUser()
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_COPILOT = "com.creativeam.goorno.ACTION_DISABLE_COPILOT"
        const val ACTION_OPEN_ORIGIN_STREET_VIEW = "com.creativeam.goorno.ACTION_OPEN_ORIGIN_STREET_VIEW"
        const val ACTION_OPEN_DEST_STREET_VIEW = "com.creativeam.goorno.ACTION_OPEN_DEST_STREET_VIEW"
        const val ACTION_DISMISS_OVERLAY_CARD = "com.creativeam.goorno.ACTION_DISMISS_OVERLAY_CARD"
        const val EXTRA_ADDRESS = "extra_street_address"
        const val STATUS_NOTIFICATION_ID = 4102
        private const val CHANNEL_ID = "goorno_status_bar_v5"
        private const val PREFS_NAME = "goorno_persisted_notification"

        /**
         * Exibe ou atualiza na barra superior do Android a notificação silenciosa com persistência total:
         * - Se uma atualização parcial chegar sem endereço ou sem tempo, preserva os dados já capturados
         *   para que NENHUMA informação suma da notificação quando a tela atualizar!
         */
        fun showActiveNotification(
            context: Context,
            latestEvaluation: FinancialEvaluationResult? = null,
            showProfit: Boolean? = null,
            showCost: Boolean? = null
        ) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                if (showProfit != null) {
                    editor.putBoolean("show_profit", showProfit)
                }
                if (showCost != null) {
                    editor.putBoolean("show_cost", showCost)
                }

                if (latestEvaluation != null && latestEvaluation.offer.offeredPrice != null) {
                    val offer = latestEvaluation.offer
                    val newPrice = offer.offeredPrice ?: 0.0
                    val prevPrice = prefs.getFloat("price", -1f).toDouble()
                    val prevTimestamp = prefs.getLong("timestamp", 0L)
                    val now = System.currentTimeMillis()

                    // Verifica se é uma releitura/atualização rápida da mesma corrida (ou dentro de 12s sem endereço novo)
                    val isSameRideOrQuickUpdate = (prevPrice > 0 && abs(newPrice - prevPrice) < 0.15) ||
                        (now - prevTimestamp < 12_000L && offer.originAddress.isNullOrBlank() && offer.destinationAddress.isNullOrBlank())

                    val prevOrigin = prefs.getString("origin_address", "") ?: ""
                    val prevDest = prefs.getString("dest_address", "") ?: ""
                    val prevMin = prefs.getInt("total_min", -1)
                    val prevKm = prefs.getFloat("total_km", -1f).toDouble()
                    val prevGrossHr = prefs.getFloat("gross_hr", -1f).toDouble()

                    // Preserva endereços e métricas já capturadas caso a nova leitura parcial venha sem eles,
                    // mas NUNCA herda endereços de corridas antigas diferentes!
                    val sanitizedOrigin = offer.originAddress?.let { BrazilianOfferParser.cleanAddressCandidate(it) }
                    val sanitizedDest = offer.destinationAddress?.let { BrazilianOfferParser.cleanAddressCandidate(it) }

                    val mergedOrigin = sanitizedOrigin?.takeIf { it.isNotBlank() }
                        ?: if (isSameRideOrQuickUpdate && prevOrigin.isNotBlank()) prevOrigin else ""
                    val mergedDest = sanitizedDest?.takeIf { it.isNotBlank() }
                        ?: if (isSameRideOrQuickUpdate && prevDest.isNotBlank()) prevDest else ""

                    val mergedMin = latestEvaluation.totalConsideredDurationMin
                        ?: if (isSameRideOrQuickUpdate && prevMin > 0) prevMin else null
                    val mergedKm = latestEvaluation.totalConsideredDistanceKm
                        ?: if (isSameRideOrQuickUpdate && prevKm > 0) prevKm else null
                    val mergedGrossHr = latestEvaluation.grossPerHour
                        ?: if (isSameRideOrQuickUpdate && prevGrossHr > 0) prevGrossHr else null

                    val prevRisk = prefs.getBoolean("has_risk", false)
                    val prevRiskBadge = prefs.getString("risk_badge", "") ?: ""
                    val mergedHasRisk = latestEvaluation.hasRiskAlert || (isSameRideOrQuickUpdate && prevRisk)
                    val mergedRiskBadge = latestEvaluation.riskAlertBadge?.takeIf { it.isNotBlank() }
                        ?: if (mergedHasRisk) prevRiskBadge else ""

                    val prevRatingBadge = prefs.getString("passenger_rating_badge", "") ?: ""
                    val mergedRatingBadge = latestEvaluation.passengerRatingBadge?.takeIf { it.isNotBlank() }
                        ?: if (isSameRideOrQuickUpdate && prevRatingBadge.isNotBlank()) prevRatingBadge else ""

                    editor.putBoolean("has_ride", true)
                    editor.putLong("timestamp", now)
                    editor.putString("verdict_name", latestEvaluation.verdict.name)
                    editor.putString("verdict_label", latestEvaluation.verdict.label)
                    editor.putString("source_app", offer.sourceApp.displayName)
                    editor.putFloat("price", newPrice.toFloat())
                    editor.putFloat("total_km", (mergedKm ?: -1.0).toFloat())
                    editor.putInt("total_min", mergedMin ?: -1)
                    editor.putFloat("gross_km", (latestEvaluation.grossPerKm ?: -1.0).toFloat())
                    editor.putFloat("gross_hr", (mergedGrossHr ?: -1.0).toFloat())
                    editor.putFloat("net_profit", (latestEvaluation.estimatedNetProfit ?: 0.0).toFloat())
                    editor.putFloat("total_cost", (latestEvaluation.estimatedTotalCost ?: 0.0).toFloat())
                    editor.putString("origin_address", mergedOrigin)
                    editor.putString("dest_address", mergedDest)
                    editor.putBoolean("has_risk", mergedHasRisk)
                    editor.putString("risk_badge", mergedRiskBadge)
                    editor.putString("passenger_rating_badge", mergedRatingBadge)
                    editor.putString("operational_badge", latestEvaluation.operationalBadge ?: "")
                }

                editor.apply()

                createNotificationChannel(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val disableIntent = Intent(context, CopilotNotificationReceiver::class.java).apply {
                    action = ACTION_DISABLE_COPILOT
                }
                val disablePendingIntent = PendingIntent.getBroadcast(
                    context,
                    101,
                    disableIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_goorno)
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .setContentIntent(openPendingIntent)

                val hasRide = prefs.getBoolean("has_ride", false)
                if (hasRide) {
                    val verdictName = prefs.getString("verdict_name", TrafficLightVerdict.GREEN.name) ?: TrafficLightVerdict.GREEN.name
                    val verdictLabel = prefs.getString("verdict_label", "Aceitar") ?: "Aceitar"
                    val sourceApp = prefs.getString("source_app", "99 Motorista") ?: "99 Motorista"
                    val price = prefs.getFloat("price", 0f).toDouble()
                    val totalKm = prefs.getFloat("total_km", -1f).toDouble()
                    val totalMin = prefs.getInt("total_min", -1)
                    val grossKm = prefs.getFloat("gross_km", -1f).toDouble()
                    val grossHr = prefs.getFloat("gross_hr", -1f).toDouble()
                    val netProfit = prefs.getFloat("net_profit", 0f).toDouble()
                    val totalCost = prefs.getFloat("total_cost", 0f).toDouble()
                    val origAddress = prefs.getString("origin_address", "") ?: ""
                    val destAddress = prefs.getString("dest_address", "") ?: ""
                    val hasRisk = prefs.getBoolean("has_risk", false)
                    val riskBadge = prefs.getString("risk_badge", "") ?: ""
                    val ratingBadge = prefs.getString("passenger_rating_badge", "") ?: ""
                    val effectiveShowProfit = prefs.getBoolean("show_profit", false)
                    val effectiveShowCost = prefs.getBoolean("show_cost", false)

                    val emoji = when (verdictName) {
                        TrafficLightVerdict.GREEN.name -> "🟢"
                        TrafficLightVerdict.YELLOW.name -> "🟡"
                        TrafficLightVerdict.RED.name -> "🔴"
                        else -> "⚪"
                    }

                    val priceStr = formatCurr(price)
                    val kmStr = if (grossKm > 0) formatCurr(grossKm) + "/km" else "--/km"
                    val hrStr = if (grossHr > 0) formatCurr(grossHr) + "/h" else "--/h"
                    val netStr = formatCurr(netProfit)
                    val costStr = formatCurr(totalCost)
                    val distStr = if (totalKm > 0) String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", totalKm) else "-- km"
                    val minStr = if (totalMin > 0) "$totalMin min" else "-- min"

                    val extraParts = mutableListOf<String>()
                    val opBadge = prefs.getString("operational_badge", "") ?: ""
                    if (opBadge.isNotBlank()) extraParts.add(opBadge)
                    if (ratingBadge.isNotBlank()) extraParts.add(ratingBadge)
                    if (effectiveShowProfit) extraParts.add("Lucro: $netStr")
                    if (effectiveShowCost) extraParts.add("Custo: $costStr")
                    val extraSuffix = if (extraParts.isEmpty()) "" else " · " + extraParts.joinToString(" · ")
                    val riskHeaderTag = if (hasRisk) " · ⚠️ RISCO" else ""

                    val origLine = origAddress.takeIf { it.isNotBlank() } ?: "Toque no histórico p/ detalhes"
                    val destLine = destAddress.takeIf { it.isNotBlank() } ?: "Toque no histórico p/ detalhes"

                    val title = "$emoji $verdictLabel$riskHeaderTag · $priceStr · $kmStr · $hrStr"
                    val shortText = "$distStr · $minStr | 📍 $origLine ➔ 🏁 $destLine"
                    val expandedText = buildString {
                        if (hasRisk && riskBadge.isNotBlank()) {
                            append("🚨 $riskBadge\n")
                        }
                        if (ratingBadge.isNotBlank()) {
                            append("Passageiro: $ratingBadge\n")
                        }
                        append("$sourceApp · $priceStr ($distStr · $minStr)\n")
                        append("$kmStr · $hrStr$extraSuffix\n")
                        append("📍 $origLine\n")
                        append("🏁 $destLine")
                    }

                    builder
                        .setContentTitle(title)
                        .setContentText(shortText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))

                    // Botão 1 na Notificação: Abrir Street View 360° da Origem (recolhe cortina e abre na hora)
                    if (origAddress.isNotBlank()) {
                        val origIntent = Intent(context, com.creativeam.goorno.activity.StreetViewTrampolineActivity::class.java).apply {
                            putExtra("EXTRA_ADDRESS", origAddress)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val origPending = PendingIntent.getActivity(
                            context,
                            201,
                            origIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(
                            android.R.drawable.ic_dialog_map,
                            "📍 Origem 360°",
                            origPending
                        )
                    }

                    // Botão 2 na Notificação: Abrir Street View 360° do Destino (recolhe cortina e abre na hora)
                    if (destAddress.isNotBlank()) {
                        val destIntent = Intent(context, com.creativeam.goorno.activity.StreetViewTrampolineActivity::class.java).apply {
                            putExtra("EXTRA_ADDRESS", destAddress)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val destPending = PendingIntent.getActivity(
                            context,
                            202,
                            destIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(
                            android.R.drawable.ic_menu_directions,
                            "🏁 Destino 360°",
                            destPending
                        )
                    }
                } else {
                    builder
                        .setContentTitle("🟢 GoOrNo Ativo")
                        .setContentText("Monitorando Uber & 99")
                }

                // Botão Desativar sempre presente na notificação superior
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "⏹️ Desativar",
                    disablePendingIntent
                )

                val notification = builder.build()
                if (!GoOrNoForegroundKeepAliveService.isServiceRunning()) {
                    try {
                        GoOrNoForegroundKeepAliveService.start(context)
                    } catch (_: Throwable) {}
                }
                nm.notify(STATUS_NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "GoOrNo — Copiloto Ativo",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                    description = "Exibe o ícone do GoOrNo ao lado do relógio e status"
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun buildActiveNotification(context: Context): android.app.Notification {
            createNotificationChannel(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_goorno)
                .setContentTitle("🟢 GoOrNo Ativo")
                .setContentText("Monitorando Uber e 99")
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(openPendingIntent)
            return builder.build()
        }

        fun cancelActiveNotification(context: Context) {
            try {
                GoOrNoForegroundKeepAliveService.stop(context)
            } catch (_: Throwable) {}
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(STATUS_NOTIFICATION_ID)
                nm.cancelAll()
            } catch (_: Exception) {
            }
        }

        private fun formatCurr(v: Double): String =
            String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", v)
    }
}
