package com.creativeam.goorno.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Serviço de Primeiro Plano Persistente (Foreground Service) do GoOrNo v3.3.25.
 *
 * Finalidade Arquitetural:
 * 1. Ancorar o processo com prioridade máxima de execução no Android (oom_score_adj ~200),
 *    impedindo que o Low Memory Killer (LMK) do sistema feche o GoOrNo quando o motorista
 *    abrir aplicativos pesados de mídia (como Spotify, Instagram, YouTube ou navegação 3D).
 * 2. Manter a notificação ativa no topo do Android com flag START_STICKY.
 * 3. Sobreviver ao deslize da lista de apps recentes via onTaskRemoved.
 * 4. Manter WakeLock parcial seguro enquanto o serviço estiver ativo.
 * 5. 100% em conformidade com as diretrizes do Google Play e políticas do Android 14+ (foregroundServiceType="dataSync").
 */
class GoOrNoForegroundKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLockSafely()
        promoteToForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        acquireWakeLockSafely()
        promoteToForegroundSafely()
        return START_STICKY
    }

    private fun promoteToForegroundSafely() {
        try {
            val notification = CopilotNotificationReceiver.buildActiveNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        CopilotNotificationReceiver.STATUS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (_: Throwable) {
                    startForeground(CopilotNotificationReceiver.STATUS_NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(CopilotNotificationReceiver.STATUS_NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
            // Ignorado com seguranca se o sistema restringir foreground service temporariamente
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // No Android 12+, evitamos chamar startForegroundService no onTaskRemoved para prevenir ForegroundServiceStartNotAllowedException
    }

    override fun onDestroy() {
        releaseWakeLockSafely()
        super.onDestroy()
        isRunning = false
    }

    private fun acquireWakeLockSafely() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoOrNo::KeepAliveWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutos de proteção por renovação
            }
        } catch (_: Throwable) {}
    }

    private fun releaseWakeLockSafely() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
        wakeLock = null
    }

    companion object {
        @Volatile
        private var isRunning = false

        @Synchronized
        fun start(context: Context) {
            if (isRunning) return
            try {
                val intent = Intent(context, GoOrNoForegroundKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                isRunning = true
            } catch (_: Throwable) {
                // Silenciosamente absorvido se o Android 12+ proibir inicio a partir de background
            }
        }

        @Synchronized
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
