package com.creativeam.goorno.camera

import java.io.File
import java.util.Locale

/**
 * Representa um vídeo gravado pela Câmera Secreta do GoOrNo.
 */
data class SecretRecording(
    val file: File,
    val id: String,
    val filename: String,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val lens: String, // "FRONTAL", "TRASEIRA"
    val quality: String, // "360p", "480p", "720p"
    val isProtected: Boolean = false
) {
    val formattedSize: String
        get() = if (fileSizeBytes >= 1024L * 1024L * 1024L) {
            String.format(Locale.getDefault(), "%.1f GB", fileSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        } else {
            val mb = (fileSizeBytes.toDouble() / (1024.0 * 1024.0)).coerceAtLeast(0.1)
            String.format(Locale.getDefault(), "%.0f MB", mb)
        }

    val formattedDuration: String
        get() = if (durationSeconds >= 60) {
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            if (s > 0) "${m}m ${s}s de vídeo" else "${m} min de vídeo"
        } else {
            "${durationSeconds} s de vídeo"
        }

    val timeRangeStr: String
        get() {
            val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            val start = sdf.format(java.util.Date(timestampMillis))
            val endMillis = timestampMillis + (durationSeconds * 1000L)
            val end = sdf.format(java.util.Date(endMillis))
            return "$start – $end"
        }

    val dateFormattedStr: String
        get() {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return sdf.format(java.util.Date(timestampMillis))
        }
}
