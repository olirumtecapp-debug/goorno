package com.creativeam.goorno

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.ocr.OcrScreenCaptureService
import com.creativeam.goorno.service.CopilotNotificationReceiver
import com.creativeam.goorno.service.GoOrNoAccessibilityService
import com.creativeam.goorno.ui.GoOrNoComposeApp
import com.creativeam.goorno.ui.theme.GoOrNoTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPerm by mutableStateOf(false)
    private var hasAccessibilityPerm by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPermissionsState()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OcrScreenCaptureService::class.java).apply {
                action = OcrScreenCaptureService.ACTION_START_OCR
                putExtra(OcrScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OcrScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        refreshPermissionsState()

        setContent {
            GoOrNoTheme {
                GoOrNoComposeApp(
                    hasOverlayPermission = hasOverlayPerm,
                    hasAccessibilityPermission = hasAccessibilityPerm,
                    onRequestOverlayPermission = { openOverlayPermissionSettings() },
                    onRequestAccessibilityPermission = { openAccessibilitySettings() },
                    onRequestOcrCapturePermission = { requestOcrMediaProjection() },
                    onStopAllBackgroundServices = { stopAllBackgroundMonitoring() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsState()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshPermissionsState() {
        hasOverlayPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        hasAccessibilityPerm = isAccessibilityServiceEnabled()
        RuntimeDiagnosticsBus.setAccessibilityConnected(hasAccessibilityPerm)

        val app = applicationContext as? GoOrNoApplication
        if (hasOverlayPerm && app?.overlayPanel?.isCopilotMonitoringEnabled() == true) {
            app.overlayPanel.showOrUpdateTopStatusBar(accessibilityEnabled = hasAccessibilityPerm)
            if (hasAccessibilityPerm) {
                CopilotNotificationReceiver.showActiveNotification(this)
            }
        } else if (app?.overlayPanel?.isCopilotMonitoringEnabled() == false) {
            app.overlayPanel.hidePanel()
            CopilotNotificationReceiver.cancelActiveNotification(this)
        }

        if (app?.overlayPanel?.isCopilotMonitoringEnabled() == false) {
            RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
        } else if (!hasOverlayPerm || !hasAccessibilityPerm) {
            if (!RuntimeDiagnosticsBus.ocrActive.value) {
                RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.PERMISSION_MISSING)
            }
        } else {
            RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val expectedComponent = "$packageName/${GoOrNoAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.split(":").any {
                it.equals(expectedComponent, ignoreCase = true) ||
                    it.contains(packageName, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openOverlayPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun requestOcrMediaProjection() {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } catch (_: Exception) {
        }
    }

    private fun stopAllBackgroundMonitoring() {
        try {
            val app = applicationContext as? GoOrNoApplication
            app?.overlayPanel?.setMonitoringEnabled(false, hasAccessibilityPerm)

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancelAll()

            val stopOcrIntent = Intent(this, OcrScreenCaptureService::class.java).apply {
                action = OcrScreenCaptureService.ACTION_STOP_OCR
            }
            startService(stopOcrIntent)
            RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.SERVICE_OFF)
        } catch (_: Exception) {
        }
    }
}
