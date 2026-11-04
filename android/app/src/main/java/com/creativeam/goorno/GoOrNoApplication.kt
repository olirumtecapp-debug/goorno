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
        appScope.launch {
            try {
                preferencesRepository.configFlow.collectLatest { cfg ->
                    try {
                        overlayPanel.applyConfig(cfg)
                        if (cfg.copilotMonitoringEnabled) {
                            com.creativeam.goorno.service.CopilotNotificationReceiver.showActiveNotification(
                                context = this@GoOrNoApplication,
                                showProfit = cfg.showProfitOnOverlay,
                                showCost = cfg.showCostOnOverlay,
                                showShortcutBubble = cfg.showFloatingShortcutBubble
                            )
                        } else {
                            com.creativeam.goorno.service.CopilotNotificationReceiver.cancelActiveNotification(this@GoOrNoApplication)
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }

    

}
