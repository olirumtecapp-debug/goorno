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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        acquireWakeLockSafely()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Se o usuário fechar o aplicativo da lista de apps recentes (swipe),
        // garante que o serviço continue rodando em primeiro plano sem ser finalizado pelo Android
        try {
            val restartIntent = Intent(applicationContext, GoOrNoForegroundKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(applicationContext, restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        } catch (_: Throwable) {}
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
