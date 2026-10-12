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
import com.creativeam.goorno.ocr.OcrScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receptor das ações clicadas diretamente na notificação da barra de status do Android
 * (onde fica o ícone do GoOrNo ao lado do relógio e do ícone da 99).
 *
 * Permite ao motorista:
 * 1. "Desativar GoOrNo" direto pela barra de notificações quando não estiver trabalhando.
 * 2. "Fechar Janela" caso queira ocultar imediatamente qualquer card aberto.
 */
class CopilotNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? GoOrNoApplication

        when (action) {
            ACTION_DISABLE_COPILOT -> {
                // 1. Desliga imediatamente o monitoramento em memória e fecha qualquer janelinha flutuante
                app?.overlayPanel?.setMonitoringEnabled(false, accessibilityEnabled = false)
                app?.overlayPanel?.hidePanel()
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)

                // 2. Cancela a notificação da barra de status e para eventual serviço OCR
                cancelActiveNotification(context)
                try {
                    context.stopService(Intent(context, OcrScreenCaptureService::class.java))
                } catch (_: Exception) {
                }

                // 3. Salva no DataStore que o motorista desativou o GoOrNo
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

            ACTION_DISMISS_OVERLAY_CARD -> {
                app?.overlayPanel?.dismissCurrentOfferCardByUser()
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_COPILOT = "com.creativeam.goorno.ACTION_DISABLE_COPILOT"
        const val ACTION_DISMISS_OVERLAY_CARD = "com.creativeam.goorno.ACTION_DISMISS_OVERLAY_CARD"
        const val STATUS_NOTIFICATION_ID = 4102
        private const val CHANNEL_ID = "goorno_active_bar_v3"

        /**
         * Exibe na barra superior do Android (ao lado do relógio e do ícone da 99) a notificação silenciosa
         * com o botão de ação direta "Desativar GoOrNo".
         */
        fun showActiveNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "GoOrNo — Status do Copiloto",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        description = "Mostra na barra superior do Android que o GoOrNo está ativo e permite desativar com 1 toque"
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

                val dismissCardIntent = Intent(context, CopilotNotificationReceiver::class.java).apply {
                    action = ACTION_DISMISS_OVERLAY_CARD
                }
                val dismissCardPendingIntent = PendingIntent.getBroadcast(
                    context,
                    102,
                    dismissCardIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("🟢 GoOrNo ATIVO — Monitorando Uber e 99")
                    .setContentText("Lendo chamadas em segundo plano. Toque abaixo para desativar.")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("Lendo chamadas automaticamente em segundo plano (sem nada flutuando na tela até tocar corrida). Toque em 'Desativar GoOrNo' quando parar de rodar.")
                    )
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(openPendingIntent)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Desativar GoOrNo",
                        disablePendingIntent
                    )
                    .addAction(
                        android.R.drawable.ic_menu_view,
                        "Fechar Janela",
                        dismissCardPendingIntent
                    )
                    .build()

                nm.notify(STATUS_NOTIFICATION_ID, notification)
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
    }
}
