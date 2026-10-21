package com.creativeam.goorno.ocr

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço Alternativo de OCR Local utilizando MediaProjection autorizado pelo usuário
 * e Google ML Kit Text Recognition (100% no dispositivo, sem enviar prints para servidores).
 */
class OcrScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastOcrOffer: StandardRideOffer? = null
    private var isProcessingFrame = false

    private val periodicScanRunnable = object : Runnable {
        override fun run() {
            captureAndRecognizeSingleFrame()
            // Intervalo controlado de 3,5 segundos para evitar consumo excessivo de bateria
            mainHandler.postDelayed(this, 3500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_OCR) {
            stopOcrSession()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startForegroundWithNotification()
            startProjection(resultCode, resultData)
        } else {
            RuntimeDiagnosticsBus.setOcrActive(false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "goorno_ocr_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GoOrNo — Leitor OCR Local",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GoOrNo — OCR Alternativo Ativo")
            .setContentText("Analisando ofertas localmente quando a Acessibilidade não fornecer texto.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        stopOcrSession()
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // Detecta quando a sessão de captura termina ou a permissão é revogada
                RuntimeDiagnosticsBus.setOcrActive(false)
                stopOcrSession()
                stopSelf()
            }
        }, mainHandler)

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels / 2).coerceAtLeast(360)
        val height = (metrics.heightPixels / 2).coerceAtLeast(640)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GoOrNoOcrDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )

        RuntimeDiagnosticsBus.setOcrActive(true)
        mainHandler.postDelayed(periodicScanRunnable, 1500L)
    }

    private fun captureAndRecognizeSingleFrame() {
        if (isProcessingFrame) return
        val reader = imageReader ?: return

        val foregroundApp = RuntimeDiagnosticsBus.lastSnapshot.value.detectedApp

        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return

        isProcessingFrame = true
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val fullBitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val roiTop = (fullBitmap.height * 0.10).toInt()
            val roiHeight = fullBitmap.height - roiTop
            val roiBitmap = Bitmap.createBitmap(fullBitmap, 0, roiTop, image.width, roiHeight)
            fullBitmap.recycle()

            val inputImage = InputImage.fromBitmap(roiBitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks.flatMap { block ->
                        block.lines.map { it.text }
                    }.filter { txt ->
                        !txt.contains("GoOrNo", ignoreCase = true) &&
                            !txt.contains("Aceitar", ignoreCase = true) &&
                            !txt.contains("Avaliar", ignoreCase = true) &&
                            !txt.contains("Recusar", ignoreCase = true) &&
                            !txt.contains("COMPENSA", ignoreCase = true) &&
                            !txt.contains("AVALIAR", ignoreCase = true)
                    }
                    roiBitmap.recycle()
                    handleRecognizedLines(lines, foregroundApp)
                    isProcessingFrame = false
                }
                .addOnFailureListener {
                    roiBitmap.recycle()
                    isProcessingFrame = false
                }
        } catch (_: Exception) {
            try {
                image.close()
            } catch (_: Exception) {
            }
            isProcessingFrame = false
        }
    }

    private fun handleRecognizedLines(lines: List<String>, targetApp: SourceApp) {
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val inferredApp = if (targetApp != SourceApp.UNKNOWN) {
            targetApp
        } else {
            BrazilianOfferParser.inferSourceAppFromScreenContent(null, lines)
        }

        var parsedOffers = when (inferredApp) {
            SourceApp.UBER_DRIVER -> UberOfferReader.parseMultipleOffersFromScreen(
                rawNodes = lines,
                captureMethod = CaptureMethod.OCR,
                timestampMillis = now,
                isSimulated = false
            )
            else -> NinetyNineOfferReader.parseMultipleOffersFromScreen(
                rawNodes = lines,
                captureMethod = CaptureMethod.OCR,
                timestampMillis = now,
                isSimulated = false
            )
        }
        if (parsedOffers.isEmpty()) {
            parsedOffers = UberOfferReader.parseMultipleOffersFromScreen(
                rawNodes = lines,
                captureMethod = CaptureMethod.OCR,
                timestampMillis = now,
                isSimulated = false
            )
        }

        if (parsedOffers.isEmpty()) {
            lastOcrOffer = null
            return
        }
        val firstOffer = parsedOffers.first()

        val dup = BrazilianOfferParser.isSameOrUpdatedOffer(lastOcrOffer, firstOffer)
        if (dup == BrazilianOfferParser.DuplicateCheckResult.EXACT_DUPLICATE) return
        lastOcrOffer = firstOffer

        val app = applicationContext as? GoOrNoApplication ?: return
        serviceScope.launch {
            val config = app.preferencesRepository.getConfigOnce()
            val evaluations = parsedOffers.map { FinancialEngine.evaluate(it, config) }
            val richestEval = evaluations.maxByOrNull { eval ->
                (if (!eval.offer.originAddress.isNullOrBlank()) 3 else 0) +
                    (if (!eval.offer.destinationAddress.isNullOrBlank()) 3 else 0) +
                    (if (eval.totalConsideredDurationMin != null) 2 else 0)
            } ?: evaluations.first()
            RuntimeDiagnosticsBus.publishEvaluation(richestEval)
            app.overlayPanel.applyConfig(config)
            app.overlayPanel.showEvaluations(evaluations)
            com.creativeam.goorno.service.CopilotNotificationReceiver.showActiveNotification(
                context = this@OcrScreenCaptureService,
                latestEvaluation = richestEval,
                showProfit = config.showProfitOnOverlay,
                showCost = config.showCostOnOverlay
            )

            if (config.saveHistoryEnabled) {
                for (evaluation in evaluations) {
                    val parsed = evaluation.offer
                    val isSanePrice = (parsed.offeredPrice ?: 0.0) >= 4.50
                    val totalKm = evaluation.totalConsideredDistanceKm ?: 0.0
                    val totalMin = evaluation.totalConsideredDurationMin ?: 0
                    val hasAddresses = !parsed.originAddress.isNullOrBlank() || !parsed.destinationAddress.isNullOrBlank()
                    val isLegitOffer = isSanePrice && totalKm >= 0.2 && totalMin > 0 && hasAddresses && !parsed.hasFieldConflict

                    // Blindagem absoluta de persistência: NUNCA grava leituras parciais, saldos ou lixo de OCR
                    if (!parsed.isSimulated && isLegitOffer) {
                        app.database.rideOfferDao().insertOffer(
                            RideOfferEntity(
                                id = parsed.offerId,
                                timestampMillis = parsed.timestampMillis,
                                sourceApp = parsed.sourceApp.displayName,
                                captureMethod = parsed.captureMethod.label,
                                rideCategory = parsed.rideCategory,
                                offeredPrice = parsed.offeredPrice ?: 0.0,
                                actualReceivedPrice = null,
                                pickupDistanceKm = parsed.pickupDistanceKm,
                                tripDistanceKm = parsed.tripDistanceKm,
                                totalDistanceKm = evaluation.totalConsideredDistanceKm,
                                pickupDurationMin = parsed.pickupDurationMin,
                                tripDurationMin = parsed.tripDurationMin,
                                totalDurationMin = evaluation.totalConsideredDurationMin,
                                grossPerKm = evaluation.grossPerKm,
                                grossPerHour = evaluation.grossPerHour,
                                estimatedTotalCost = evaluation.estimatedTotalCost,
                                estimatedNetProfit = evaluation.estimatedNetProfit,
                                netProfitPerKm = evaluation.netProfitPerKm,
                                netProfitPerHour = evaluation.netProfitPerHour,
                                verdict = evaluation.verdict.name,
                                reasonsSummary = (evaluation.reasons + evaluation.warnings).joinToString(" | "),
                                originAddress = parsed.originAddress,
                                destinationAddress = parsed.destinationAddress,
                                confidenceScore = parsed.confidenceScore,
                                isPartialReading = !evaluation.isAnalysisComplete,
                                isSimulated = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun stopOcrSession() {
        mainHandler.removeCallbacks(periodicScanRunnable)
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        RuntimeDiagnosticsBus.setOcrActive(false)
    }

    override fun onDestroy() {
        stopOcrSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_OCR = "com.creativeam.goorno.action.START_OCR"
        const val ACTION_STOP_OCR = "com.creativeam.goorno.action.STOP_OCR"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val NOTIFICATION_ID = 4099
    }
}
