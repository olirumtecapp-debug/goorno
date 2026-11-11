package com.creativeam.goorno.camera

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.MainActivity
import com.creativeam.goorno.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço de Primeiro Plano para Câmera Secreta em Segundo Plano.
 * Grava áudio e vídeo em segundo plano (enquanto Uber e 99 estão na tela),
 * dividindo em blocos de 1 a 15 minutos e operando em loop contínuo.
 */
class SecretCameraRecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var dummyPreviewSurface: Surface? = null

    private var currentOutputFile: File? = null
    private var chunkDurationMs: Long = 5 * 60 * 1000L
    private var maxStorageGb: Int = 64
    private var currentQuality: String = "BAIXA"
    private var currentLens: String = "FRONTAL"

    private var chunkStartTimeMillis: Long = 0L
    private var elapsedSecondsTotal: Int = 0

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (_isRecordingState.value) {
                elapsedSecondsTotal++
                _elapsedSecondsState.value = elapsedSecondsTotal
                updateNotification()

                val chunkElapsed = System.currentTimeMillis() - chunkStartTimeMillis
                if (chunkElapsed >= chunkDurationMs) {
                    rotateRecordingChunk()
                } else {
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_RECORDING
        when (action) {
            ACTION_START_RECORDING -> {
                val app = applicationContext as? GoOrNoApplication
                val config = app?.preferencesRepository?.getInMemoryConfig()
                currentQuality = config?.secretCameraQuality ?: "BAIXA"
                currentLens = config?.secretCameraLens ?: "FRONTAL"
                chunkDurationMs = ((config?.secretCameraCutMinutes ?: 5).coerceIn(1, 15) * 60 * 1000L)
                maxStorageGb = config?.secretCameraMaxStorageGb ?: 64

                startForeground(NOTIFICATION_ID, buildNotification())
                startCameraRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopCameraRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SWITCH_LENS -> {
                currentLens = if (currentLens == "FRONTAL") "TRASEIRA" else "FRONTAL"
                _currentLensState.value = currentLens
                val app = applicationContext as? GoOrNoApplication
                app?.let {
                    serviceScope.launch {
                        val currentConfig = it.preferencesRepository.getConfigOnce()
                        it.preferencesRepository.saveConfig(currentConfig.copy(secretCameraLens = currentLens))
                    }
                }
                restartCameraWithCurrentSettings()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startCameraRecording() {
        if (_isRecordingState.value) return

        try {
            val targetCameraId = findCameraIdForLens(currentLens) ?: run {
                Toast.makeText(this, "Câmera não encontrada para a lente $currentLens", Toast.LENGTH_SHORT).show()
                stopSelf()
                return
            }

            val repository = (applicationContext as? GoOrNoApplication)?.let { SecretRecordingRepository(it) }
                ?: SecretRecordingRepository(this)
            val dir = repository.getRecordingsDirectory()
            val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val qualityTag = when (currentQuality) {
                "ALTA" -> "720p"
                "MEDIA" -> "480p"
                else -> "360p"
            }
            val fileName = "REC_${timeTag}_${currentLens}_${qualityTag}.mp4"
            currentOutputFile = File(dir, fileName)

            setupMediaRecorder(currentOutputFile!!)

            cameraManager?.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    _isRecordingState.value = false
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    _isRecordingState.value = false
                }
            }, mainHandler)

        } catch (e: Throwable) {
            Toast.makeText(this, "Erro ao iniciar gravação da câmera: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun setupMediaRecorder(outputFile: File) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        val (width, height, bitrate) = when (currentQuality) {
            "ALTA" -> Triple(1280, 720, 3_500_000)
            "MEDIA" -> Triple(854, 480, 1_800_000)
            else -> Triple(640, 360, 1_000_000)
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(20)
            setVideoEncodingBitRate(bitrate)
            setOrientationHint(if (currentLens == "FRONTAL") 270 else 90)
            setOutputFile(outputFile.absolutePath)
            prepare()
        }
        mediaRecorder = recorder
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val recorder = mediaRecorder ?: return

        try {
            dummySurfaceTexture = SurfaceTexture(10).apply {
                setDefaultBufferSize(640, 360)
            }
            dummyPreviewSurface = Surface(dummySurfaceTexture)
            val recorderSurface = recorder.surface

            val surfaces = listOf(dummyPreviewSurface!!, recorderSurface)

            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val recordBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(dummyPreviewSurface!!)
                            addTarget(recorderSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }

                        session.setRepeatingRequest(recordBuilder.build(), null, mainHandler)
                        recorder.start()

                        chunkStartTimeMillis = System.currentTimeMillis()
                        _isRecordingState.value = true
                        _currentLensState.value = currentLens

                        mainHandler.removeCallbacks(timerRunnable)
                        mainHandler.post(timerRunnable)

                    } catch (e: Throwable) {
                        stopCameraRecording()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    stopCameraRecording()
                }
            }, mainHandler)

        } catch (e: Throwable) {
            stopCameraRecording()
        }
    }

    private fun rotateRecordingChunk() {
        serviceScope.launch {
            try {
                // Para o gravador atual
                try {
                    mediaRecorder?.stop()
                } catch (_: Throwable) {}
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null

                // Aplica cota em loop
                val repository = (applicationContext as? GoOrNoApplication)?.let { SecretRecordingRepository(it) }
                    ?: SecretRecordingRepository(applicationContext)
                repository.refreshRecordings()
                repository.enforceStorageQuota(maxStorageGb)

                // Prepara próximo chunk
                val dir = repository.getRecordingsDirectory()
                val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val qualityTag = when (currentQuality) {
                    "ALTA" -> "720p"
                    "MEDIA" -> "480p"
                    else -> "360p"
                }
                val fileName = "REC_${timeTag}_${currentLens}_${qualityTag}.mp4"
                currentOutputFile = File(dir, fileName)

                setupMediaRecorder(currentOutputFile!!)

                val recorderSurface = mediaRecorder!!.surface
                val recordBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                    dummyPreviewSurface?.let { addTarget(it) }
                    addTarget(recorderSurface)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                }
                if (recordBuilder != null && captureSession != null) {
                    captureSession?.setRepeatingRequest(recordBuilder.build(), null, mainHandler)
                    mediaRecorder?.start()
                    chunkStartTimeMillis = System.currentTimeMillis()
                } else {
                    restartCameraWithCurrentSettings()
                }

                mainHandler.post(timerRunnable)
            } catch (_: Throwable) {
                restartCameraWithCurrentSettings()
            }
        }
    }

    private fun restartCameraWithCurrentSettings() {
        stopCameraResources()
        startCameraRecording()
    }

    private fun stopCameraRecording() {
        mainHandler.removeCallbacks(timerRunnable)
        _isRecordingState.value = false
        elapsedSecondsTotal = 0
        _elapsedSecondsState.value = 0

        stopCameraResources()

        serviceScope.launch {
            val repository = (applicationContext as? GoOrNoApplication)?.let { SecretRecordingRepository(it) }
                ?: SecretRecordingRepository(applicationContext)
            repository.refreshRecordings()
            repository.enforceStorageQuota(maxStorageGb)
        }
    }

    private fun stopCameraResources() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Throwable) {}
        captureSession = null

        try {
            mediaRecorder?.stop()
        } catch (_: Throwable) {}
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Throwable) {}
        mediaRecorder = null

        try {
            cameraDevice?.close()
        } catch (_: Throwable) {}
        cameraDevice = null

        try {
            dummyPreviewSurface?.release()
            dummySurfaceTexture?.release()
        } catch (_: Throwable) {}
        dummyPreviewSurface = null
        dummySurfaceTexture = null
    }

    private fun findCameraIdForLens(lens: String): String? {
        val cm = cameraManager ?: return null
        val targetFacing = if (lens == "FRONTAL") {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == targetFacing) {
                return id
            }
        }
        return cm.cameraIdList.firstOrNull()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            301,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SecretCameraRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            302,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val switchIntent = Intent(this, SecretCameraRecordingService::class.java).apply {
            action = ACTION_SWITCH_LENS
        }
        val switchPendingIntent = PendingIntent.getService(
            this,
            303,
            switchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mm = elapsedSecondsTotal / 60
        val ss = elapsedSecondsTotal % 60
        val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
        val lensLabel = if (currentLens == "FRONTAL") "Interior" else "Via/Para-brisa"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goorno)
            .setContentTitle("🔴 Câmera Secreta GoOrNo · $timeFormatted")
            .setContentText("Gravando $lensLabel em segundo plano com segurança.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ Parar", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "🔄 Lente", switchPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GoOrNo Câmera Secreta",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gravação de segurança em segundo plano para motoristas"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopCameraRecording()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RECORDING = "com.creativeam.goorno.camera.START"
        const val ACTION_STOP_RECORDING = "com.creativeam.goorno.camera.STOP"
        const val ACTION_SWITCH_LENS = "com.creativeam.goorno.camera.SWITCH_LENS"

        private const val NOTIFICATION_ID = 5102
        private const val CHANNEL_ID = "goorno_camera_recording_channel"

        private val _isRecordingState = MutableStateFlow(false)
        val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

        private val _elapsedSecondsState = MutableStateFlow(0)
        val elapsedSecondsState: StateFlow<Int> = _elapsedSecondsState.asStateFlow()

        private val _currentLensState = MutableStateFlow("FRONTAL")
        val currentLensState: StateFlow<String> = _currentLensState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SecretCameraRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SecretCameraRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }

        fun switchLens(context: Context) {
            val intent = Intent(context, SecretCameraRecordingService::class.java).apply {
                action = ACTION_SWITCH_LENS
            }
            context.startService(intent)
        }
    }
}
