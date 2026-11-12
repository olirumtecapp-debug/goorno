package com.creativeam.goorno

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.creativeam.goorno.data.local.GoOrNoDatabase
import com.creativeam.goorno.data.preferences.DriverPreferencesRepository
import com.creativeam.goorno.camera.SecretRecordingRepository
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

    lateinit var secretRecordingRepository: SecretRecordingRepository
        private set

    lateinit var overlayPanel: FloatingOverlayPanel
        private set

    fun isOverlayPanelInitialized(): Boolean = ::overlayPanel.isInitialized

    override fun onCreate() {
        super.onCreate()
        installGlobalCrashShield()

        try {
            database = GoOrNoDatabase.getInstance(this)
        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha ao inicializar database", t)
        }

        try {
            preferencesRepository = DriverPreferencesRepository(this)
        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha ao inicializar preferencesRepository", t)
        }

        try {
            secretRecordingRepository = SecretRecordingRepository(this)
        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha ao inicializar secretRecordingRepository", t)
        }

        try {
            overlayPanel = FloatingOverlayPanel(this).apply {
                onUserRequestedDisableCopilot = {
                    appScope.launch {
                        try {
                            val current = preferencesRepository.getConfigOnce()
                            preferencesRepository.saveConfig(current.copy(copilotMonitoringEnabled = false))
                            RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancelAll()
                        } catch (_: Throwable) {}
                    }
                }
                onUserRequestedHideShortcutBubble = {
                    appScope.launch {
                        try {
                            val current = preferencesRepository.getConfigOnce()
                            preferencesRepository.saveConfig(current.copy(showFloatingShortcutBubble = false))
                        } catch (_: Throwable) {}
                    }
                }
                onUserUpdatedSemaforoQuickly = { minKm, minHr, minRating, maxPickupKm ->
                    appScope.launch {
                        try {
                            val current = preferencesRepository.getConfigOnce()
                            preferencesRepository.saveConfig(current.copy(
                                targetGrossPerKm = minKm,
                                targetGrossPerHour = minHr,
                                targetGoodPassengerRating = minRating,
                                distantPickupKmThreshold = maxPickupKm
                            ))
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha segura ao criar overlayPanel", t)
        }

        try {
            com.creativeam.goorno.data.auth.GoogleAuthManager.init(this)
        } catch (t: Throwable) {
            Log.e("GoOrNoApp", "Falha segura ao inicializar GoogleAuthManager", t)
        }

        // Mantem todas as configuracoes do motorista sincronizadas continuamente em memoria
        appScope.launch {
            try {
                if (::preferencesRepository.isInitialized && ::overlayPanel.isInitialized) {
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
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Blindagem Global contra Falhas e Fechamento Inesperado (CrashShield).
     */
    private fun installGlobalCrashShield() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GoOrNoCrashShield", "\uD83D\uDEE1\uFE0F Excecao interceptada na thread '${thread.name}': ${throwable.message}", throwable)
            if (throwable is OutOfMemoryError) {
                try {
                    System.gc()
                } catch (_: Throwable) {}
                return@setDefaultUncaughtExceptionHandler
            }

            val isRecoverable = throwable is android.view.WindowManager.BadTokenException ||
                throwable is android.view.WindowManager.InvalidDisplayException ||
                throwable is IllegalStateException ||
                throwable is SecurityException ||
                throwable is IllegalArgumentException ||
                throwable is NullPointerException ||
                throwable is UninitializedPropertyAccessException ||
                throwable is java.util.ConcurrentModificationException ||
                throwable.javaClass.name.contains("ForegroundService") ||
                throwable.javaClass.name.contains("DeadSystem") ||
                throwable.javaClass.name.contains("TransactionTooLarge") ||
                throwable.javaClass.name.contains("GoogleId") ||
                throwable.javaClass.name.contains("Credential")

            if (isRecoverable) {
                Log.w("GoOrNoCrashShield", "🛡️ Erro recuperavel absorvido silenciosamente: ${throwable.javaClass.simpleName} - ${throwable.message}")
                if (thread.name == "main") {
                    try {
                        Looper.loop()
                    } catch (_: Throwable) {}
                }
                return@setDefaultUncaughtExceptionHandler
            }

            if (thread.name != "main") {
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                originalHandler?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {}
        }
    }
}
