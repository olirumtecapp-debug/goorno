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
            onUserUpdatedSemaforoQuickly = { minKm, minHr, minRating ->
                appScope.launch {
                    val current = preferencesRepository.getConfigOnce()
                    preferencesRepository.saveConfig(current.copy(
                        targetGrossPerKm = minKm,
                        targetGrossPerHour = minHr,
                        targetGoodPassengerRating = minRating
                    ))
                }
            }
        }

        // Mantém todas as configurações do motorista sincronizadas continuamente em memória
        // mesmo que o motorista abra WhatsApp, Waze ou outros aplicativos de terceiros!
        appScope.launch {
            preferencesRepository.configFlow.collectLatest { cfg ->
                overlayPanel.applyConfig(cfg)
            }
        }
    }
}
