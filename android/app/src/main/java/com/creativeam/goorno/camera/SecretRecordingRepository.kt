package com.creativeam.goorno.camera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Repositório para gerenciamento 100% local dos vídeos da Câmera Secreta.
 * Não consome internet e opera em loop circular (FIFO).
 */
class SecretRecordingRepository(private val context: Context) {

    private val _recordingsFlow = MutableStateFlow<List<SecretRecording>>(emptyList())
    val recordingsFlow: Flow<List<SecretRecording>> = _recordingsFlow.asStateFlow()

    fun getRecordingsDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val cameraDir = File(baseDir, "GoOrNo_Gravacoes")
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }
        return cameraDir
    }

    private fun getThumbnailsDirectory(): File {
        val thumbsDir = File(context.cacheDir, "recordings_thumbs")
        if (!thumbsDir.exists()) {
            thumbsDir.mkdirs()
        }
        return thumbsDir
    }

    suspend fun refreshRecordings(): List<SecretRecording> = withContext(Dispatchers.IO) {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
            ?: emptyArray()

        val list = files.mapNotNull { file ->
            try {
                parseRecordingFromFile(file)
            } catch (_: Throwable) {
                null
            }
        }.sortedByDescending { it.timestampMillis }

        _recordingsFlow.value = list
        list
    }

    private fun parseRecordingFromFile(file: File): SecretRecording {
        val name = file.nameWithoutExtension
        // Exemplo de formato de nome: REC_20261111_062315_FRONT_360p.mp4 ou REC_timestamp
        var timestamp = file.lastModified()
        var lens = "FRONTAL"
        var quality = "360p"

        if (name.contains("TRASEIRA", ignoreCase = true) || name.contains("BACK", ignoreCase = true)) {
            lens = "TRASEIRA"
        }
        if (name.contains("720p", ignoreCase = true)) quality = "720p"
        else if (name.contains("480p", ignoreCase = true)) quality = "480p"

        var durationSec = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationSec = (durStr.toLong() / 1000).toInt()
            }
            retriever.release()
        } catch (_: Throwable) {
            // Estima duração se falhar leitura rápida
            val estSec = (file.length() / (150 * 1024)).toInt().coerceAtLeast(1)
            durationSec = estSec
        }

        return SecretRecording(
            file = file,
            id = file.name,
            filename = file.name,
            timestampMillis = timestamp,
            durationSeconds = durationSec,
            fileSizeBytes = file.length(),
            lens = lens,
            quality = quality,
            isProtected = false
        )
    }

    fun getTotalUsedSpaceBytes(): Long {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
            ?: return 0L
        return files.sumOf { it.length() }
    }

    suspend fun deleteRecording(recording: SecretRecording): Boolean = withContext(Dispatchers.IO) {
        try {
            val thumb = File(getThumbnailsDirectory(), "${recording.file.name}.jpg")
            if (thumb.exists()) thumb.delete()
            val deleted = recording.file.delete()
            refreshRecordings()
            deleted
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun clearAllRecordings(): Unit = withContext(Dispatchers.IO) {
        val dir = getRecordingsDirectory()
        dir.listFiles()?.forEach { it.delete() }
        getThumbnailsDirectory().listFiles()?.forEach { it.delete() }
        refreshRecordings()
    }

    /**
     * Aplica o algoritmo de Gravação em Loop (FIFO):
     * Se o espaço total ocupado ultrapassar a cota limite em GB (ex: 64 GB),
     * remove os vídeos mais antigos até voltar para dentro do limite seguro.
     */
    suspend fun enforceStorageQuota(maxStorageGb: Int) = withContext(Dispatchers.IO) {
        val maxBytes = maxStorageGb.toLong() * 1024L * 1024L * 1024L
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
            ?.sortedBy { it.lastModified() } // Mais antigos primeiro
            ?: return@withContext

        var currentTotal = files.sumOf { it.length() }
        for (f in files) {
            if (currentTotal <= maxBytes) break
            val size = f.length()
            if (f.delete()) {
                currentTotal -= size
            }
        }
        refreshRecordings()
    }

    /**
     * Extrai miniatura (thumbnail) do vídeo gravado para exibição na galeria.
     */
    suspend fun getOrGenerateThumbnail(recording: SecretRecording): Bitmap? = withContext(Dispatchers.IO) {
        val thumbFile = File(getThumbnailsDirectory(), "${recording.file.name}.jpg")
        if (thumbFile.exists() && thumbFile.length() > 0) {
            try {
                return@withContext android.graphics.BitmapFactory.decodeFile(thumbFile.absolutePath)
            } catch (_: Throwable) {}
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(recording.file.absolutePath)
            val bmp = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()

            if (bmp != null) {
                // Salva em cache para não reprocessar no scroll
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
                return@withContext bmp
            }
        } catch (_: Throwable) {}

        null
    }
}
