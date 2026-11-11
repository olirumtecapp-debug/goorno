package com.creativeam.goorno.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.creativeam.goorno.camera.SecretCameraRecordingService
import com.creativeam.goorno.camera.SecretRecording
import com.creativeam.goorno.camera.SecretRecordingRepository
import com.creativeam.goorno.model.DriverFinancialConfig
import com.creativeam.goorno.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Componentes de Interface para a funcionalidade Câmera Secreta do GoOrNo.
 * Inspirado fielmente no design e experiência de uso do concorrente Rota Pro.
 * 100% Gratuito e com armazenamento estritamente local (R$ 0,00 de custo).
 */

@Composable
fun SecretCameraHomeCard(
    config: DriverFinancialConfig,
    isRecording: Boolean,
    recordingSeconds: Int,
    isPermsGranted: Boolean,
    onOpenSettings: () -> Unit,
    onOpenRecordings: () -> Unit,
    onToggleRecording: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, if (isRecording) Color(0xFFFCA5A5) else CleanCardBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isRecording) Color(0xFFFEE2E2) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Câmera Secreta",
                                tint = if (isRecording) Color(0xFFDC2626) else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Câmera Secreta",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = PetrolDarkText
                        )
                        Text(
                            text = if (isRecording) "Gravando em segundo plano" else "Gravação discreta das viagens",
                            fontSize = 11.5.sp,
                            color = if (isRecording) Color(0xFFDC2626) else MutedText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustes da Câmera",
                            tint = MutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = isRecording,
                        onCheckedChange = { checked ->
                            if (checked && !isPermsGranted) {
                                onRequestPermissions()
                            } else {
                                onToggleRecording(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TrafficGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                if (!isPermsGranted) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.clickable { onRequestPermissions() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFD97706), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sem permissão",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                } else if (isRecording) {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFDC2626), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val m = recordingSeconds / 60
                            val s = recordingSeconds % 60
                            Text(
                                text = String.format(Locale.getDefault(), "● REC · %02d:%02d", m, s),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF94A3B8), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Desligada",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Botão Gravações >
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.clickable { onOpenRecordings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Gravações >",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecretCameraSettingsScreenCard(
    config: DriverFinancialConfig,
    isRecording: Boolean,
    onBack: () -> Unit,
    onOpenRecordings: () -> Unit,
    onSaveConfig: (DriverFinancialConfig) -> Unit,
    onOpenCutDurationPicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PetrolDarkText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Câmera Secreta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = PetrolDarkText
                )
                Text(
                    text = if (isRecording) "Gravando agora" else "Desligada",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) Color(0xFFDC2626) else MutedText
                )
            }
        }

        // Section 1: QUALIDADE DA GRAVAÇÃO
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QUALIDADE DA GRAVAÇÃO",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, CleanCardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Baixa (360p)
                    QualityRowItem(
                        title = "Baixa",
                        resolution = "360p",
                        isSelected = config.secretCameraQuality == "BAIXA",
                        onClick = { onSaveConfig(config.copy(secretCameraQuality = "BAIXA")) }
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // Média (480p)
                    QualityRowItem(
                        title = "Média",
                        resolution = "480p",
                        isSelected = config.secretCameraQuality == "MEDIA",
                        onClick = { onSaveConfig(config.copy(secretCameraQuality = "MEDIA")) }
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // Alta (720p)
                    QualityRowItem(
                        title = "Alta",
                        resolution = "720p",
                        isSelected = config.secretCameraQuality == "ALTA",
                        onClick = { onSaveConfig(config.copy(secretCameraQuality = "ALTA")) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Recomendado pro seu aparelho: Baixa (360p) — equilibra a gravação com o cálculo de ganhos.",
                fontSize = 11.5.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        // Section 2: ESCOLHA DA LENTE
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ESCOLHA DA LENTE",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, CleanCardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Frontal
                    LensRowItem(
                        title = "Frontal",
                        subtitle = "Interior do carro",
                        icon = Icons.Default.Person,
                        iconColor = Color(0xFF2563EB),
                        isSelected = config.secretCameraLens == "FRONTAL",
                        onClick = { onSaveConfig(config.copy(secretCameraLens = "FRONTAL")) }
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // Traseira
                    LensRowItem(
                        title = "Traseira",
                        subtitle = "Via / para-brisa",
                        icon = Icons.Default.AltRoute,
                        iconColor = Color(0xFF7C3AED),
                        isSelected = config.secretCameraLens == "TRASEIRA",
                        onClick = { onSaveConfig(config.copy(secretCameraLens = "TRASEIRA")) }
                    )
                }
            }
        }

        // Section 3: TEMPO DE CORTE
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TEMPO DE CORTE",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, CleanCardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${config.secretCameraCutMinutes} min por arquivo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color(0xFF0F172A)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Stepper [ - ] [ + ]
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val next = (config.secretCameraCutMinutes - 1).coerceAtLeast(1)
                                        onSaveConfig(config.copy(secretCameraCutMinutes = next))
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                }
                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(1.dp)
                                        .background(Color(0xFFCBD5E1))
                                )
                                IconButton(
                                    onClick = {
                                        val next = (config.secretCameraCutMinutes + 1).coerceAtMost(15)
                                        onSaveConfig(config.copy(secretCameraCutMinutes = next))
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Edit / List icon
                        IconButton(
                            onClick = onOpenCutDurationPicker,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Escolher corte", tint = TrafficGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A gravação é dividida em vídeos de ${config.secretCameraCutMinutes} min — mais fácil de revisar e compartilhar.",
                fontSize = 11.5.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Info Notes
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("ⓘ", fontSize = 14.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "A gravação não tem limite de tempo: continua até você desligar. Ao passar de ${config.secretCameraMaxStorageGb} GB, os trechos mais antigos dão lugar aos novos (ajuste o espaço na tela Gravações).",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 17.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.Top) {
                        Text("🛡️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gravação secreta: nada aparece na tela durante as corridas. Os vídeos ficam salvos com segurança na memória do seu celular.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRowItem(
    title: String,
    resolution: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = resolution,
                fontSize = 13.5.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selecionado",
                    tint = TrafficGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LensRowItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = iconColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selecionado",
                tint = TrafficGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SecretRecordingsScreenCard(
    config: DriverFinancialConfig,
    repository: SecretRecordingRepository,
    onBack: () -> Unit,
    onPlayVideo: (SecretRecording) -> Unit,
    onNotify: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val recordings by repository.recordingsFlow.collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("Tudo") }
    var showConfirmDeleteAllDialog by remember { mutableStateOf(false) }
    var showStorageLimitDialog by remember { mutableStateOf(false) }
    var recordingToDelete by remember { mutableStateOf<SecretRecording?>(null) }

    LaunchedEffect(Unit) {
        repository.refreshRecordings()
    }

    val usedBytes = remember(recordings) {
        recordings.sumOf { it.fileSizeBytes }
    }
    val usedGbFormatted = remember(usedBytes) {
        String.format(Locale.getDefault(), "%.1f", usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    }
    val quotaGb = config.secretCameraMaxStorageGb
    val quotaBytes = quotaGb.toLong() * 1024L * 1024L * 1024L
    val progressFraction = remember(usedBytes, quotaBytes) {
        if (quotaBytes > 0) (usedBytes.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val filteredList = remember(recordings, selectedFilter) {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        when (selectedFilter) {
            "Hoje" -> recordings.filter { now - it.timestampMillis < oneDayMs }
            "Ontem" -> recordings.filter {
                val diff = now - it.timestampMillis
                diff in oneDayMs..(2 * oneDayMs)
            }
            "7 dias" -> recordings.filter { now - it.timestampMillis < 7 * oneDayMs }
            else -> recordings
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PetrolDarkText)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Gravações",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = PetrolDarkText
                )
            }

            if (recordings.isNotEmpty()) {
                IconButton(onClick = { showConfirmDeleteAllDialog = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir todas", tint = Color(0xFFEF4444))
                }
            }
        }

        // Section ARMAZENAMENTO
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ARMAZENAMENTO",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, CleanCardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Card Espaço Usado
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStorageLimitDialog = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Espaço usado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$usedGbFormatted GB de $quotaGb GB reservados",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = progressFraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = TrafficGreen,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    }

                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // Card Salvando na memória
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF2563EB),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Salvando na memória do telefone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "100% local e seguro no seu aparelho",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ao encher, as gravações antigas dão lugar às novas.",
                fontSize = 11.5.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Tudo", "Hoje", "Ontem", "7 dias").forEach { chip ->
                val isSelected = selectedFilter == chip
                Surface(
                    color = if (isSelected) Color.White else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFCBD5E1) else Color.Transparent),
                    modifier = Modifier.clickable { selectedFilter = chip }
                ) {
                    Text(
                        text = chip,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Recordings List or Empty View
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma gravação neste período",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "VÍDEOS GRAVADOS (${filteredList.size})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                filteredList.forEach { rec ->
                    RecordingItemCard(
                        recording = rec,
                        repository = repository,
                        onClick = { onPlayVideo(rec) },
                        onDelete = { recordingToDelete = rec }
                    )
                }
            }
        }
    }

    // Confirm Delete Single Video Dialog
    if (recordingToDelete != null) {
        val target = recordingToDelete!!
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Excluir gravação?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja apagar o vídeo das ${target.timeRangeStr}? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteRecording(target)
                            onNotify("Gravação excluída")
                        }
                        recordingToDelete = null
                    }
                ) {
                    Text("Excluir", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirm Delete All Videos Dialog
    if (showConfirmDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAllDialog = false },
            title = { Text("Excluir todas as gravações?", fontWeight = FontWeight.Bold) },
            text = { Text("Todos os vídeos da Câmera Secreta serão apagados do seu aparelho para liberar espaço. Confirma?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearAllRecordings()
                            onNotify("Todas as gravações foram excluídas")
                        }
                        showConfirmDeleteAllDialog = false
                    }
                ) {
                    Text("Apagar Tudo", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Storage Quota Limit Dialog
    if (showStorageLimitDialog) {
        val app = LocalContext.current.applicationContext as? com.creativeam.goorno.GoOrNoApplication
        AlertDialog(
            onDismissRequest = { showStorageLimitDialog = false },
            title = { Text("Limite de Armazenamento", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text(
                        text = "Escolha quanto espaço da memória do seu celular pode ser reservado para os vídeos em loop contínuo:",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    listOf(16, 32, 64, 128).forEach { gb ->
                        val isSel = config.secretCameraMaxStorageGb == gb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    app?.let {
                                        coroutineScope.launch {
                                            it.preferencesRepository.saveConfig(config.copy(secretCameraMaxStorageGb = gb))
                                        }
                                    }
                                    showStorageLimitDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$gb GB reservados",
                                fontSize = 14.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSel) {
                                Text("✓", color = TrafficGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageLimitDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun RecordingItemCard(
    recording: SecretRecording,
    repository: SecretRecordingRepository,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var thumbBitmap by remember(recording.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(recording.id) {
        withContext(Dispatchers.IO) {
            val bmp = repository.getOrGenerateThumbnail(recording)
            withContext(Dispatchers.Main) {
                thumbBitmap = bmp
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with badge
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.BottomStart
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = "Miniatura do vídeo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
                    }
                }

                // Duration Badge
                val m = recording.durationSeconds / 60
                val s = recording.durationSeconds % 60
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 6.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "▶ %02d:%02d", m, s),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.timeRangeStr,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Recuperada · ${recording.formattedDuration} · ${recording.formattedSize}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Player de Vídeo Nativo com Marca d'Água de Segurança (Data, Hora Exata e 'Gravado com GoOrNo').
 * Permite reproduzir, pausar, avançar e compartilhar diretamente para WhatsApp ou autoridades.
 */
@Composable
fun SecretVideoPlayerDialog(
    recording: SecretRecording,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val formattedWatermarkDate = remember(recording.timestampMillis) {
        val sdf = SimpleDateFormat("EEE, dd 'de' MMM 'de' yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
        sdf.format(Date(recording.timestampMillis))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Video View
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(recording.file.absolutePath)
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Watermark Top Overlay (Inspirado no print Rota Pro: "qua, 11 de nov de 2026 06:24:43 \n Gravado com GoOrNo")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 18.dp, end = 18.dp)
                ) {
                    Text(
                        text = formattedWatermarkDate,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 4f
                            )
                        )
                    )
                    Text(
                        text = "Gravado com GoOrNo",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 4f
                            )
                        )
                    )
                }

                // Controls Bottom Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }

                    // Share button
                    Button(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    recording.file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Gravação de Segurança GoOrNo")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Gravação de segurança GoOrNo · ${recording.dateFormattedStr} (${recording.timeRangeStr})"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Vídeo de Segurança"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro ao compartilhar vídeo: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrafficGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartilhar", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Permissões de Câmera e Microfone (Visual Rota Pro com ícones coloridos e texto explicativo).
 */
@Composable
fun CameraPermissionConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFCFCFD),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Câmera Secreta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF64748B))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Câmera e microfone",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pra gravar suas corridas em segredo, o GoOrNo precisa de acesso à câmera e ao microfone do seu aparelho.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "PRECISAMOS DESSE ACESSO PARA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item 1: Câmera
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFEF4444),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Gravar vídeo pela lente frontal ou traseira durante as corridas.",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                // Item 2: Microfone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFF59E0B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Capturar o áudio de dentro do carro junto com a imagem.",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                // Item 3: Proteção local
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF3B82F6),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Salvar as gravações protegidas no seu aparelho.",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = TrafficGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Permitir acesso", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Agora não", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TrafficGreen)
                }
            }
        }
    )
}

/**
 * Diálogo Seletor de Tempo de Corte (1 min a 15 min por arquivo).
 */
@Composable
fun CutDurationPickerDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tempo de corte",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF64748B))
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items((1..15).toList()) { minutes ->
                    val isSelected = currentMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMinutes(minutes) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$minutes min por arquivo",
                            fontSize = 14.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFF334155)
                        )
                        if (isSelected) {
                            Text("✓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TrafficGreen)
                        }
                    }
                    Divider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
                }
            }
        },
        confirmButton = {}
    )
}
