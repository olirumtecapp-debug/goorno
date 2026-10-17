package com.creativeam.goorno.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.creativeam.goorno.GoOrNoApplication

/**
 * Serviço de Primeiro Plano Persistente (Foreground Service) do GoOrNo v3.3.13.
 *
 * Finalidade Arquitetural:
 * 1. Ancorar o processo com prioridade máxima de execução no Android (oom_score_adj ~200),
 *    impedindo que o Low Memory Killer (LMK) do sistema feche o GoOrNo quando o motorista
 *    abrir aplicativos pesados de mídia (como Spotify, Instagram, YouTube ou navegação 3D).
 * 2. Manter a notificação ativa no topo do Android com flag START_STICKY.
 * 3. 100% em conformidade com as diretrizes do Google Play e políticas do Android 14+ (foregroundServiceType="dataSync").
 */
class GoOrNoForegroundKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = CopilotNotificationReceiver.buildActiveNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CopilotNotificationReceiver.STATUS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(CopilotNotificationReceiver.STATUS_NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Se o monitoramento continuar ativado pelo motorista, reinicia o serviço preventivamente
        val app = applicationContext as? GoOrNoApplication
        if (app?.overlayPanel?.isCopilotMonitoringEnabled() == true) {
            start(applicationContext)
        }
    }

    companion object {
        private var isRunning = false

        fun start(context: Context) {
            try {
                val intent = Intent(context, GoOrNoForegroundKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                isRunning = true
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, GoOrNoForegroundKeepAliveService::class.java)
                context.stopService(intent)
                isRunning = false
            } catch (_: Throwable) {
            }
        }

        fun isServiceRunning(): Boolean = isRunning
    }
}
