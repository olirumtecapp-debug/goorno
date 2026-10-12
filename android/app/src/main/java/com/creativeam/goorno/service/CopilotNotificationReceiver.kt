package com.creativeam.goorno.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.creativeam.goorno.util.StreetViewLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Receptor das ações clicadas diretamente na notificação da barra de status do Android
 * (onde fica o ícone do GoOrNo ao lado do relógio e do ícone da 99).
 *
 * Exibe:
 * - Os dados completos da ÚLTIMA CORRIDA LIDA (Semáforo, Valor, R$/km, R$/h, Lucro, Endereço de Origem e Destino).
 * - Botão 1: "📍 Origem 360°" -> Abre direto o Street View 360° da rua de embarque!
 * - Botão 2: "🏁 Destino 360°" -> Abre direto o Street View 360° da rua de destino!
 * - Botão 3: "🔴 Desativar" -> Desliga o GoOrNo e remove a notificação quando o motorista parar de rodar!
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
        private const val CHANNEL_ID = "goorno_active_bar_v4"

        @Volatile
        private var cachedLastEvaluation: FinancialEvaluationResult? = null

        /**
         * Exibe ou atualiza na barra superior do Android a notificação silenciosa com:
         * - Resumo completo da última corrida lida (Valor, R$/km, R$/h, Lucro, Origem e Destino)
         * - Botões para abrir Origem 360° (Street View), Destino 360° (Street View) e Desativar GoOrNo.
         */
        fun showActiveNotification(
            context: Context,
            latestEvaluation: FinancialEvaluationResult? = cachedLastEvaluation
        ) {
            if (latestEvaluation != null) {
                cachedLastEvaluation = latestEvaluation
            }
            val eval = cachedLastEvaluation

            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "GoOrNo — Copiloto e Última Corrida",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        description = "Mostra na barra superior o status do GoOrNo, os dados da última corrida e botões para Street View 360° e Desativar"
                    }
                    nm.createNotificationChannel(channel)
                }

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
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(openPendingIntent)

                if (eval != null) {
                    val offer = eval.offer
                    val emoji = when (eval.verdict) {
                        TrafficLightVerdict.GREEN -> "🟢"
                        TrafficLightVerdict.YELLOW -> "🟡"
                        TrafficLightVerdict.RED -> "🔴"
                        TrafficLightVerdict.GRAY_NEUTRAL -> "⚪"
                    }
                    val priceStr = offer.offeredPrice?.let { formatCurr(it) } ?: "R$ --"
                    val kmStr = eval.grossPerKm?.let { formatCurr(it) + "/km" } ?: "--/km"
                    val hrStr = eval.grossPerHour?.let { formatCurr(it) + "/h" } ?: "--/h"
                    val netStr = eval.estimatedNetProfit?.let { formatCurr(it) } ?: "--"
                    val distStr = eval.totalConsideredDistanceKm?.let {
                        String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
                    } ?: "-- km"
                    val minStr = eval.totalConsideredDurationMin?.let { "$it min" } ?: "-- min"

                    val title = "$emoji $priceStr (Total: $distStr · $minStr) · $kmStr"
                    val origLine = offer.originAddress?.takeIf { it.isNotBlank() } ?: "Não informado na tela"
                    val destLine = offer.destinationAddress?.takeIf { it.isNotBlank() } ?: "Não informado na tela"
                    val shortText = "Lucro: $netStr ($hrStr) | 📍 $origLine ➔ 🏁 $destLine"
                    val expandedText = buildString {
                        append("${offer.sourceApp.displayName} · Total: $distStr em $minStr\n")
                        append("$kmStr · $hrStr · Lucro Líq.: $netStr\n")
                        append("📍 Origem: $origLine\n")
                        append("🏁 Destino: $destLine")
                    }

                    builder
                        .setContentTitle(title)
                        .setContentText(shortText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))

                    // Botão 1 na Notificação: Abrir Street View 360° da Origem (se disponível)
                    if (!offer.originAddress.isNullOrBlank()) {
                        val origIntent = Intent(context, CopilotNotificationReceiver::class.java).apply {
                            action = ACTION_OPEN_ORIGIN_STREET_VIEW
                            putExtra(EXTRA_ADDRESS, offer.originAddress)
                        }
                        val origPending = PendingIntent.getBroadcast(
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

                    // Botão 2 na Notificação: Abrir Street View 360° do Destino (se disponível)
                    if (!offer.destinationAddress.isNullOrBlank()) {
                        val destIntent = Intent(context, CopilotNotificationReceiver::class.java).apply {
                            action = ACTION_OPEN_DEST_STREET_VIEW
                            putExtra(EXTRA_ADDRESS, offer.destinationAddress)
                        }
                        val destPending = PendingIntent.getBroadcast(
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
                        .setContentTitle("🟢 GoOrNo ATIVO — Monitorando Uber e 99")
                        .setContentText("Aguardando chamadas. Aqui aparecerão os dados e Street View da última corrida.")
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText("Lendo chamadas automaticamente em segundo plano. Assim que uma corrida tocar, você verá aqui o cálculo, endereço de Origem/Destino com botões 360° Street View, ou pode tocar em 'Desativar' quando parar de rodar.")
                        )
                }

                // Botão 3 (sempre presente): Desativar GoOrNo
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "🔴 Desativar",
                    disablePendingIntent
                )

                nm.notify(STATUS_NOTIFICATION_ID, builder.build())
            } catch (_: Exception) {
            }
        }

        fun cancelActiveNotification(context: Context) {
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
