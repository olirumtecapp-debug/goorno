package com.creativeam.goorno

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.creativeam.goorno.data.local.GoOrNoDatabase
import com.creativeam.goorno.data.preferences.DriverPreferencesRepository
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.overlay.FloatingOverlayPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.os.Handler
import android.os.Looper
import android.util.Log

class GoOrNoApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: GoOrNoDatabase
        private set

    lateinit var preferencesRepository: DriverPreferencesRepository
        private set

    lateinit var overlayPanel: FloatingOverlayPanel
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            installGlobalCrashShield()
        database = GoOrNoDatabase.getInstance(this)
        preferencesRepository = DriverPreferencesRepository(this)
        overlayPanel = FloatingOverlayPanel(this).apply {
            onUserRequestedDisableCopilot = {
                appScope.launch {
                    val current = preferencesRepository.getConfigOnce()
                    preferencesRepository.saveConfig(current.copy(copilotMonitoringEnabled = false))
                    RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
                    try {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancelAll()
                    } catch (_: Exception) {
                    }
                }
            }
            onUserRequestedHideShortcutBubble = {
                appScope.launch {
                    val current = preferencesRepository.getConfigOnce()
                    preferencesRepository.saveConfig(current.copy(showFloatingShortcutBubble = false))
                }
            }
            onUserUpdatedSemaforoQuickly = { minKm, minHr, minRating, maxPickupKm ->
                appScope.launch {
                    val current = preferencesRepository.getConfigOnce()
                    preferencesRepository.saveConfig(current.copy(
                        targetGrossPerKm = minKm,
                        targetGrossPerHour = minHr,
                        targetGoodPassengerRating = minRating,
                        distantPickupKmThreshold = maxPickupKm
                    ))
                }
            }
        }

        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha segura em onCreate", t)
        }

        // Mantém todas as configurações do motorista sincronizadas continuamente em memória
        // e ancora o Foreground Service para blindagem total contra o LMK do Android (Spotify, Instagram, etc)
        appScope.launch {
            preferencesRepository.configFlow.collectLatest { cfg ->
                overlayPanel.applyConfig(cfg)
                if (cfg.copilotMonitoringEnabled) {
                    com.creativeam.goorno.service.GoOrNoForegroundKeepAliveService.start(this@GoOrNoApplication)
                    com.creativeam.goorno.service.CopilotNotificationReceiver.showActiveNotification(
                        context = this@GoOrNoApplication,
                        showProfit = cfg.showProfitOnOverlay,
                        showCost = cfg.showCostOnOverlay
                    )
                } else {
                    com.creativeam.goorno.service.GoOrNoForegroundKeepAliveService.stop(this@GoOrNoApplication)
                    com.creativeam.goorno.service.CopilotNotificationReceiver.cancelActiveNotification(this@GoOrNoApplication)
                }
            }
        }
    }

    /**
     * Blindagem Global contra Falhas e Fechamento Inesperado (CrashShield).
     *
     * 1. Intercepta exceções não tratadas no Main Looper (UI thread), impedindo o diálogo
     *    "O GoOrNo continua apresentando falhas. Fechar app".
     * 2. Intercepta erros graves em segundo plano (como OutOfMemoryError, StaleObjectException,
     *    BadTokenException) e aciona limpeza de RAM (System.gc()) silenciosa.
     */
    private fun installGlobalCrashShield() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GoOrNoCrashShield", "🛡️ Exceção interceptada na thread '${thread.name}': ${throwable.message}", throwable)
            if (throwable is OutOfMemoryError) {
                try {
                    System.gc()
                } catch (_: Throwable) {}
            }

            // Se for na thread secundária (leitura, acessibilidade, corrotina), absorve para não derrubar o app
            if (thread.name != "main") {
                return@setDefaultUncaughtExceptionHandler
            }

            // Se for na thread principal, entrega ao handler original para encerramento limpo sem corrupção
            originalHandler?.uncaughtException(thread, throwable)
        }
    }
}