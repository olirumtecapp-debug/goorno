package com.creativeam.goorno.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.R
import com.creativeam.goorno.data.cloud.CloudSyncManager
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.detector.ForegroundAppDetector
import com.creativeam.goorno.diagnostics.*
import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.*
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import com.creativeam.goorno.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * As 10 Telas Oficiais do GoOrNo v2.2.0 — Interface Clean, Clara e Moderna.
 */
enum class GoOrNoScreen(val stepNumber: Int, val title: String, val shortTab: String) {
    HOME(1, "Painel Principal — Copiloto", "Início"),
    DRIVER_SETUP(2, "Motorista & Janela Flutuante", "Motorista"),
    VEHICLE_COSTS(3, "Veículo e Custos Operacionais", "Custos"),
    FINANCIAL_TARGETS(4, "Metas Financeiras e Semáforo", "Metas"),
    SERVICES_ACTIVATION(5, "Permissões e Serviços de Leitura", "Serviços"),
    CONNECTION_STATUS(6, "Conexão com Uber e 99", "Uber / 99"),
    HISTORY(7, "Histórico e Auditoria de Chamadas", "Histórico"),
    FINANCIAL_SUMMARY(8, "Resumo Financeiro", "Resumo"),
    DIAGNOSTICS_TESTS(9, "Diagnóstico, OCR e Simulador", "Testes"),
    PRIVACY_BACKUP(10, "Privacidade e Troca de Aparelho", "Backup")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoOrNoComposeApp(
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestOcrCapturePermission: () -> Unit,
    onStopAllBackgroundServices: () -> Unit
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as GoOrNoApplication }
    val scope = rememberCoroutineScope()

    val config by app.preferencesRepository.configFlow.collectAsState(initial = DriverFinancialConfig())
    val realHistory by app.database.rideOfferDao().observeRealOffers().collectAsState(initial = emptyList())

    val runtimeState by RuntimeDiagnosticsBus.runtimeState.collectAsState()
    val accessibilityConnected by RuntimeDiagnosticsBus.accessibilityConnected.collectAsState()
    val ocrActive by RuntimeDiagnosticsBus.ocrActive.collectAsState()
    val lastSnapshot by RuntimeDiagnosticsBus.lastSnapshot.collectAsState()
    val lastRealEvaluation by RuntimeDiagnosticsBus.lastRealEvaluation.collectAsState()
    val lastSimulatedEvaluation by RuntimeDiagnosticsBus.lastSimulatedEvaluation.collectAsState()

    var currentScreen by remember { mutableStateOf(GoOrNoScreen.HOME) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val notifyUser: (String) -> Unit = { msg ->
        statusMessage = msg
        Toast.makeText(context, "✅ $msg", Toast.LENGTH_SHORT).show()
    }

    val installedApps = remember(currentScreen) {
        ForegroundAppDetector.checkInstalledRideApps(context)
    }

    Scaffold(
        topBar = {
            Surface(
                color = PureWhiteSurface,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Superior Verde-Petróleo Oficial com Logo e Versão v2.3.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PetrolHeader)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_goorno),
                                contentDescription = "Logo GoOrNo",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF04241E))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "GoOrNo",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 19.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = LimeBright,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "v2.5.0",
                                            color = PetrolDarkText,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = runtimeState.label,
                                    fontSize = 11.sp,
                                    color = if (accessibilityConnected || ocrActive) Color(0xFFA7F3D0) else Color(0xFFFDE68A)
                                )
                            }
                        }

                        // Botão Pílula Moderno de Ajuste Rápido da Janela
                        FilledTonalButton(
                            onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = LimeBright,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${config.overlayAutoMinimizeSec}s · ${config.overlayOpacityPercent}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Seletor Clean Horizontal das 10 Telas (Pílulas Modernas em Fundo Claro)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PureWhiteSurface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GoOrNoScreen.entries.forEach { screen ->
                            val selected = currentScreen == screen
                            Surface(
                                color = if (selected) PetrolHeader else SoftSurfaceVariant,
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, if (selected) PetrolHeader else CleanCardBorder),
                                modifier = Modifier.clickable { currentScreen = screen }
                            ) {
                                Text(
                                    text = "${screen.stepNumber}. ${screen.shortTab}",
                                    color = if (selected) Color.White else PetrolDarkText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Barra de Confirmação Fixa (Sempre visível acima das abas inferiores ao salvar qualquer tela)
                if (statusMessage != null) {
                    Surface(
                        color = TrafficGreen,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusMessage!!,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { statusMessage = null }) {
                                Text("OK", color = LimeBright, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                NavigationBar(
                    containerColor = PureWhiteSurface,
                    tonalElevation = 8.dp
                ) {
                    val quickTabs = listOf(
                        Triple(GoOrNoScreen.HOME, Icons.Default.Dashboard, "Início"),
                        Triple(GoOrNoScreen.VEHICLE_COSTS, Icons.Default.LocalGasStation, "Custos"),
                        Triple(GoOrNoScreen.FINANCIAL_TARGETS, Icons.Default.Tune, "Metas"),
                        Triple(GoOrNoScreen.HISTORY, Icons.Default.History, "Histórico"),
                        Triple(GoOrNoScreen.FINANCIAL_SUMMARY, Icons.Default.BarChart, "Resumo")
                    )
                    quickTabs.forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = PetrolHeader,
                                indicatorColor = PetrolHeader,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(CleanLightBg)
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item {
                Text(
                    text = "${currentScreen.stepNumber}. ${currentScreen.title}",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = PetrolHeader
                )
            }

            when (currentScreen) {
                // =====================================================
                // TELA 1: TELA INICIAL (COPILOTO & ÚLTIMA OFERTA REAL)
                // =====================================================
                GoOrNoScreen.HOME -> {
                    item {
                        val isPermsReady = hasOverlayPermission && (hasAccessibilityPermission || ocrActive)
                        val isCopilotActive = isPermsReady && config.copilotMonitoringEnabled
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(
                                1.5.dp,
                                when {
                                    !config.copilotMonitoringEnabled -> TrafficRed
                                    isCopilotActive -> TrafficGreen
                                    else -> TrafficYellow
                                }
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = when {
                                                !config.copilotMonitoringEnabled -> TrafficRedSoft
                                                isCopilotActive -> TrafficGreenSoft
                                                else -> TrafficYellowSoft
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    !config.copilotMonitoringEnabled -> "🔴 COPILOTO DESATIVADO (FORA DE USO)"
                                                    isCopilotActive -> "🟢 COPILOTO ATIVO E PRONTO"
                                                    else -> "🟡 FALTA ATIVAR PERMISSÃO ABAIXO"
                                                },
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.5.sp,
                                                color = when {
                                                    !config.copilotMonitoringEnabled -> TrafficRed
                                                    isCopilotActive -> TrafficGreen
                                                    else -> TrafficYellow
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = when {
                                                !config.copilotMonitoringEnabled ->
                                                    "O GoOrNo está desligado e a barrinha do topo foi ocultada. Toque em 'Ligar Copiloto' abaixo quando for rodar."
                                                isCopilotActive ->
                                                    "A janelinha abre SOZINHA ao tocar chamada ou abrir ofertas na Uber/99 e some após ${config.overlayAutoMinimizeSec}s."
                                                else ->
                                                    "Ative os 2 botões abaixo para o GoOrNo ler as ofertas da Uber e da 99 automaticamente."
                                            },
                                            fontSize = 12.sp,
                                            color = MutedText
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    // Botão Ligar / Desligar Copiloto (ou Ativar permissão se faltar)
                                    Button(
                                        onClick = {
                                            if (!hasOverlayPermission) {
                                                onRequestOverlayPermission()
                                            } else if (!hasAccessibilityPermission) {
                                                onRequestAccessibilityPermission()
                                            } else {
                                                val newEnabled = !config.copilotMonitoringEnabled
                                                val updated = config.copy(copilotMonitoringEnabled = newEnabled)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    app.overlayPanel.setMonitoringEnabled(newEnabled, hasAccessibilityPermission)
                                                    if (!newEnabled) {
                                                        onStopAllBackgroundServices()
                                                        notifyUser("GoOrNo desativado! A barrinha do topo e a leitura foram desligadas.")
                                                    } else {
                                                        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                                                        notifyUser("GoOrNo ligado! Monitorando chamadas da Uber e 99.")
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = when {
                                                !isPermsReady -> PetrolHeader
                                                config.copilotMonitoringEnabled -> TrafficRed
                                                else -> TrafficGreen
                                            },
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (config.copilotMonitoringEnabled && isPermsReady)
                                                Icons.Default.PowerSettingsNew
                                            else
                                                Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                !isPermsReady -> "Ativar"
                                                config.copilotMonitoringEnabled -> "Desativar"
                                                else -> "Ligar Copiloto"
                                            },
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                // Seletor Moderno de Pílulas do Tempo da Janelinha na Tela
                                Text(
                                    text = "Tempo que a janelinha fica aberta antes de sumir:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PetrolDarkText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(5, 8, 10, 12, 15).forEach { sec ->
                                        val selected = config.overlayAutoMinimizeSec == sec
                                        FilledTonalButton(
                                            onClick = {
                                                val updated = config.copy(overlayAutoMinimizeSec = sec)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    app.overlayPanel.applyConfig(updated)
                                                    notifyUser("Tempo da janelinha definido para ${sec} segundos!")
                                                }
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (selected) PetrolHeader else SoftSurfaceVariant,
                                                contentColor = if (selected) Color.White else PetrolDarkText
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Text("${sec}s", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = CleanCardBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Checklist Rápido das 2 Permissões Essenciais
                                PermissionRowItem(
                                    number = "1",
                                    title = "Janela Flutuante (Sobrepor a outros apps)",
                                    subtitle = "Exibe a barrinha ativa no topo e o painel sobre a Uber/99",
                                    granted = hasOverlayPermission,
                                    onAction = onRequestOverlayPermission
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PermissionRowItem(
                                    number = "2",
                                    title = "Leitor Automático (Acessibilidade GoOrNo)",
                                    subtitle = "Lê R$, km, minutos e endereços automaticamente",
                                    granted = hasAccessibilityPermission,
                                    onAction = onRequestAccessibilityPermission
                                )
                            }
                        }
                    }

                    // Última Oferta Real Analisada (com Botões 1-Toque Street View Origem e Destino)
                    item {
                        val latestEval = lastRealEvaluation
                        val latestDbOffer = realHistory.firstOrNull()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ÚLTIMA OFERTA REAL DETECTADA",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (latestEval != null) {
                                    EvaluationDetailCardContent(
                                        result = latestEval,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) }
                                    )
                                } else if (latestDbOffer != null) {
                                    HistoricalOfferCardContent(
                                        entity = latestDbOffer,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) },
                                        onUpdateReceivedPrice = { newVal ->
                                            scope.launch {
                                                app.database.rideOfferDao().updateActualReceivedPrice(latestDbOffer.id, newVal)
                                                notifyUser("Valor recebido conferido e salvo!")
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "Nenhuma chamada real da Uber ou 99 foi capturada ainda nesta sessão. Quando tocar uma corrida na Uber Driver ou 99 Motorista, ela aparecerá automaticamente na janela flutuante e ficará gravada aqui.",
                                        fontSize = 12.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 2: CONFIGURAÇÃO DO MOTORISTA & TRANSPARÊNCIA
                // =====================================================
                GoOrNoScreen.DRIVER_SETUP -> {
                    item {
                        var driverName by remember(config.driverName) { mutableStateOf(config.driverName) }
                        var vehicleModel by remember(config.vehicleModel) { mutableStateOf(config.vehicleModel) }
                        var opacity by remember(config.overlayOpacityPercent) {
                            mutableFloatStateOf(config.overlayOpacityPercent.toFloat())
                        }
                        var autoMinSec by remember(config.overlayAutoMinimizeSec) {
                            mutableStateOf(config.overlayAutoMinimizeSec.toString())
                        }
                        var savedFlag by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = driverName,
                                    onValueChange = { driverName = it; savedFlag = false },
                                    label = { Text("Nome ou Apelido do Motorista") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = vehicleModel,
                                    onValueChange = { vehicleModel = it; savedFlag = false },
                                    label = { Text("Modelo do Veículo (ex: Onix 1.0, HB20, Kwid)") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Transparência da Janelinha Flutuante: ${opacity.toInt()}%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Ajuste a opacidade para enxergar o mapa da Uber e da 99 por baixo do card sem bloquear a visão.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                Slider(
                                    value = opacity,
                                    onValueChange = {
                                        opacity = it
                                        savedFlag = false
                                        val updated = config.copy(overlayOpacityPercent = it.toInt())
                                        app.overlayPanel.applyConfig(updated)
                                    },
                                    valueRange = 25f..100f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PetrolHeader,
                                        activeTrackColor = TrafficGreen
                                    )
                                )

                                OutlinedTextField(
                                    value = autoMinSec,
                                    onValueChange = { autoMinSec = it; savedFlag = false },
                                    label = { Text("Tempo que a janelinha fica aberta antes de sumir (segundos)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            driverName = driverName.ifBlank { "Motorista" },
                                            vehicleModel = vehicleModel.ifBlank { "Veículo" },
                                            overlayOpacityPercent = opacity.toInt().coerceIn(25, 100),
                                            overlayAutoMinimizeSec = (autoMinSec.toIntOrNull() ?: 10).coerceIn(3, 60)
                                        )
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            app.overlayPanel.applyConfig(updated)
                                            savedFlag = true
                                            notifyUser("Configurações do motorista e janelinha salvas com sucesso!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (savedFlag) TrafficGreen else PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (savedFlag) Color.White else LimeBright)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (savedFlag) "✓ Configuração Salva com Sucesso!" else "Salvar Configuração do Motorista",
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 3: CONFIGURAÇÃO DO VEÍCULO E DOS CUSTOS
                // =====================================================
                GoOrNoScreen.VEHICLE_COSTS -> {
                    item {
                        var kmPerLiter by remember(config.fuelConsumptionKmPerLiter) {
                            mutableStateOf(config.fuelConsumptionKmPerLiter.toString())
                        }
                        var fuelPrice by remember(config.fuelPricePerLiter) {
                            mutableStateOf(config.fuelPricePerLiter.toString())
                        }
                        var maintPerKm by remember(config.maintenanceCostPerKm) {
                            mutableStateOf(config.maintenanceCostPerKm.toString())
                        }
                        var extraPerRide by remember(config.fixedExtraCostPerRide) {
                            mutableStateOf(config.fixedExtraCostPerRide.toString())
                        }
                        var savedCostsFlag by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = String.format(
                                            Locale.forLanguageTag("pt-BR"),
                                            "Custo Operacional Atual: R$ %.2f / km\n(Combustível: R$ %.2f/km + Manutenção: R$ %.2f/km)",
                                            config.totalVariableCostPerKm,
                                            config.fuelCostPerKm,
                                            config.maintenanceCostPerKm
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = PetrolHeader,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = kmPerLiter,
                                    onValueChange = { kmPerLiter = it.replace(",", "."); savedCostsFlag = false },
                                    label = { Text("Consumo médio do veículo (km/l)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = fuelPrice,
                                    onValueChange = { fuelPrice = it.replace(",", "."); savedCostsFlag = false },
                                    label = { Text("Preço do combustível por litro (R$/l)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = maintPerKm,
                                    onValueChange = { maintPerKm = it.replace(",", "."); savedCostsFlag = false },
                                    label = { Text("Custo de manutenção, pneus e desgaste por km (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = extraPerRide,
                                    onValueChange = { extraPerRide = it.replace(",", "."); savedCostsFlag = false },
                                    label = { Text("Custo fixo adicional por corrida (R$) — opcional") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text(
                                    text = "Nota: Os custos são estimativas operacionais para separar claramente Receita Bruta de Lucro Líquido Estimado, sem contabilizar duas vezes o mesmo item.",
                                    fontSize = 11.sp,
                                    color = MutedText
                                )

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            fuelConsumptionKmPerLiter = (kmPerLiter.toDoubleOrNull() ?: 10.5).coerceAtLeast(1.0),
                                            fuelPricePerLiter = (fuelPrice.toDoubleOrNull() ?: 5.89).coerceAtLeast(0.0),
                                            maintenanceCostPerKm = (maintPerKm.toDoubleOrNull() ?: 0.25).coerceAtLeast(0.0),
                                            fixedExtraCostPerRide = (extraPerRide.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                        )
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            savedCostsFlag = true
                                            notifyUser("Custos operacionais do veículo salvos com sucesso!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (savedCostsFlag) TrafficGreen else PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (savedCostsFlag) Color.White else LimeBright)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (savedCostsFlag) "✓ Custos Salvos com Sucesso!" else "Salvar Custos Operacionais",
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 4: CONFIGURAÇÃO DAS METAS FINANCEIRAS E SEMÁFORO
                // =====================================================
                GoOrNoScreen.FINANCIAL_TARGETS -> {
                    item {
                        var grossKm by remember(config.targetGrossPerKm) {
                            mutableStateOf(config.targetGrossPerKm.toString())
                        }
                        var grossHr by remember(config.targetGrossPerHour) {
                            mutableStateOf(config.targetGrossPerHour.toString())
                        }
                        var netKm by remember(config.targetNetPerKm) {
                            mutableStateOf(config.targetNetPerKm.toString())
                        }
                        var netHr by remember(config.targetNetPerHour) {
                            mutableStateOf(config.targetNetPerHour.toString())
                        }
                        var reqGrossKm by remember(config.requireGrossPerKm) {
                            mutableStateOf(config.requireGrossPerKm)
                        }
                        var reqGrossHr by remember(config.requireGrossPerHour) {
                            mutableStateOf(config.requireGrossPerHour)
                        }
                        var reqNetKm by remember(config.requireNetPerKm) {
                            mutableStateOf(config.requireNetPerKm)
                        }
                        var reqNetHr by remember(config.requireNetPerHour) {
                            mutableStateOf(config.requireNetPerHour)
                        }
                        var savedTargetsFlag by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Defina os valores mínimos para o Semáforo classificar uma corrida como VERDE e escolha quais metas são obrigatórias:",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                OutlinedTextField(
                                    value = grossKm,
                                    onValueChange = { grossKm = it.replace(",", "."); savedTargetsFlag = false },
                                    label = { Text("Meta Mínima de Ganho Bruto por KM (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqGrossKm, onCheckedChange = { reqGrossKm = it; savedTargetsFlag = false })
                                    Text("Exigir Ganho Bruto/km na classificação", fontSize = 12.sp, color = PetrolDarkText)
                                }

                                OutlinedTextField(
                                    value = grossHr,
                                    onValueChange = { grossHr = it.replace(",", "."); savedTargetsFlag = false },
                                    label = { Text("Meta Mínima de Ganho Bruto por Hora (R$/h)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqGrossHr, onCheckedChange = { reqGrossHr = it; savedTargetsFlag = false })
                                    Text("Exigir Ganho Bruto/hora na classificação", fontSize = 12.sp, color = PetrolDarkText)
                                }

                                OutlinedTextField(
                                    value = netKm,
                                    onValueChange = { netKm = it.replace(",", "."); savedTargetsFlag = false },
                                    label = { Text("Meta de Lucro Líquido por KM (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqNetKm, onCheckedChange = { reqNetKm = it; savedTargetsFlag = false })
                                    Text("Exigir Lucro Líquido/km na classificação", fontSize = 12.sp, color = PetrolDarkText)
                                }

                                OutlinedTextField(
                                    value = netHr,
                                    onValueChange = { netHr = it.replace(",", "."); savedTargetsFlag = false },
                                    label = { Text("Meta de Lucro Líquido por Hora (R$/h)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqNetHr, onCheckedChange = { reqNetHr = it; savedTargetsFlag = false })
                                    Text("Exigir Lucro Líquido/hora na classificação", fontSize = 12.sp, color = PetrolDarkText)
                                }

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            targetGrossPerKm = (grossKm.toDoubleOrNull() ?: 2.0).coerceAtLeast(0.5),
                                            targetGrossPerHour = (grossHr.toDoubleOrNull() ?: 40.0).coerceAtLeast(5.0),
                                            targetNetPerKm = (netKm.toDoubleOrNull() ?: 1.3).coerceAtLeast(0.1),
                                            targetNetPerHour = (netHr.toDoubleOrNull() ?: 28.0).coerceAtLeast(5.0),
                                            requireGrossPerKm = reqGrossKm,
                                            requireGrossPerHour = reqGrossHr,
                                            requireNetPerKm = reqNetKm,
                                            requireNetPerHour = reqNetHr
                                        )
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            savedTargetsFlag = true
                                            notifyUser("Metas financeiras e critérios do semáforo salvos com sucesso!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (savedTargetsFlag) TrafficGreen else PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (savedTargetsFlag) Color.White else LimeBright)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (savedTargetsFlag) "✓ Metas Salvas com Sucesso!" else "Salvar Metas Financeiras",
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 5: ATIVAÇÃO DOS SERVIÇOS DE LEITURA
                // =====================================================
                GoOrNoScreen.SERVICES_ACTIVATION -> {
                    item {
                        var showAdvancedOcr by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Permissões Obrigatórias do Copiloto (Apenas 2 Passos):",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                Text(
                                    text = "1. Permissão de Sobreposição (Janela Flutuante)",
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = if (hasOverlayPermission) "Status: CONCEDIDA ✓" else "Status: PENDENTE ⚠️",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasOverlayPermission) TrafficGreen else TrafficYellow
                                )
                                Button(
                                    onClick = onRequestOverlayPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = LimeBright, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Configurar Sobreposição", fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "2. Leitor Automático (Acessibilidade GoOrNo)",
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = if (hasAccessibilityPermission)
                                        "Status: ATIVO E MONITORANDO ✓"
                                    else
                                        "Status: DESATIVADO (Toque abaixo para habilitar nas Configurações do Android)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasAccessibilityPermission) TrafficGreen else TrafficYellow
                                )
                                Button(
                                    onClick = onRequestAccessibilityPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = LimeBright, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Configurações de Acessibilidade", fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Atalho discreto para Modo Avançado (OCR) e Diagnóstico/Simulador
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showAdvancedOcr = !showAdvancedOcr },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SoftSurfaceVariant,
                                            contentColor = PetrolDarkText
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (showAdvancedOcr) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Modo Avançado (OCR)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = { currentScreen = GoOrNoScreen.DIAGNOSTICS_TESTS },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SoftSurfaceVariant,
                                            contentColor = PetrolHeader
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Diagnóstico & Simulador", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showAdvancedOcr) {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Leitor Reserva por Imagem (OCR — Opcional)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = if (ocrActive)
                                                    "Status OCR: ATIVO EM SEGUNDO PLANO ✓"
                                                else
                                                    "Não é necessário ativar no dia a dia. Use apenas se a Uber ou 99 bloquearem a leitura padrão por Acessibilidade.",
                                                fontSize = 11.5.sp,
                                                color = MutedText
                                            )
                                            FilledTonalButton(
                                                onClick = onRequestOcrCapturePermission,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = PureWhiteSurface,
                                                    contentColor = PetrolHeader
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (ocrActive) "Reiniciar Captura OCR" else "Autorizar Captura OCR (Opcional)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Botão claro para encerrar todos os monitoramentos em segundo plano (Seção 9)
                                Button(
                                    onClick = {
                                        onStopAllBackgroundServices()
                                        notifyUser("Painel flutuante e captura OCR encerrados com sucesso.")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TrafficRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.StopCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fechar Janelinha Flutuante", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 6: STATUS DA CONEXÃO COM UBER E 99
                // =====================================================
                GoOrNoScreen.CONNECTION_STATUS -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Verificação Real dos Pacotes no Aparelho",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )

                                // Uber Driver Status
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Uber Driver (Motorista)",
                                            fontWeight = FontWeight.Black,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = if (installedApps.uberInstalled)
                                                "Instalado no aparelho ✓ (Pacote: ${installedApps.uberPackageFound})"
                                            else
                                                "Não detectado neste aparelho (Pacote esperado: com.ubercab.driver)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (installedApps.uberInstalled) TrafficGreen else TrafficYellow
                                        )
                                    }
                                }

                                // 99 Motorista Status
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "99 Motorista",
                                            fontWeight = FontWeight.Black,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = if (installedApps.ninetyNineInstalled)
                                                "Instalado no aparelho ✓ (Pacote: ${installedApps.ninetyNinePackageFound})"
                                            else
                                                "Não detectado neste aparelho (Pacotes: com.taxis99.driver / com.didiglobal.driver)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (installedApps.ninetyNineInstalled) TrafficGreen else TrafficYellow
                                        )
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Última Janela Detectada em Primeiro Plano:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Pacote: ${lastSnapshot.packageName}\nApp Identificado: ${lastSnapshot.detectedApp.displayName}\nElementos de texto lidos: ${lastSnapshot.nodeCount}",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 7: HISTÓRICO DE ANÁLISES E CONFERÊNCIA DE VALOR
                // =====================================================
                GoOrNoScreen.HISTORY -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total de ofertas reais gravadas: ${realHistory.size}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PetrolDarkText
                            )
                            if (realHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            app.database.rideOfferDao().clearAllRealOffers()
                                            notifyUser("Histórico local limpo.")
                                        }
                                    }
                                ) {
                                    Text("Limpar Histórico", color = TrafficRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (realHistory.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                                border = BorderStroke(1.dp, CleanCardBorder),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Nenhuma oferta real gravada ainda. Nota importante: Testes feitos no Simulador nunca são misturados neste histórico real.",
                                    fontSize = 12.sp,
                                    color = MutedText,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(realHistory, key = { it.id }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = BorderStroke(1.dp, CleanCardBorder),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    HistoricalOfferCardContent(
                                        entity = item,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) },
                                        onUpdateReceivedPrice = { valPaid ->
                                            scope.launch {
                                                app.database.rideOfferDao().updateActualReceivedPrice(item.id, valPaid)
                                                notifyUser("Valor recebido conferido com sucesso!")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 8: RESUMO FINANCEIRO
                // =====================================================
                GoOrNoScreen.FINANCIAL_SUMMARY -> {
                    item {
                        val totalOffers = realHistory.size
                        val greenCount = realHistory.count { it.verdict == TrafficLightVerdict.GREEN.name }
                        val yellowCount = realHistory.count { it.verdict == TrafficLightVerdict.YELLOW.name }
                        val redCount = realHistory.count { it.verdict == TrafficLightVerdict.RED.name }

                        val sumGross = realHistory.sumOf { it.offeredPrice }
                        val sumCost = realHistory.sumOf { it.estimatedTotalCost ?: 0.0 }
                        val sumNet = realHistory.sumOf { it.estimatedNetProfit ?: 0.0 }
                        val avgGrossKm = realHistory.mapNotNull { it.grossPerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                        val avgGrossHr = realHistory.mapNotNull { it.grossPerHour }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Consolidado das Ofertas Reais Analisadas",
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Ofertas Analisadas: $totalOffers  (🟢 $greenCount  |  🟡 $yellowCount  |  🔴 $redCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PetrolDarkText
                                )
                                HorizontalDivider(color = CleanCardBorder)
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Receita Bruta Ofertada Total: R$ %.2f", sumGross),
                                    fontSize = 13.sp,
                                    color = PetrolDarkText
                                )
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Custo Operacional Estimado Total: R$ %.2f", sumCost),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TrafficYellow
                                )
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Lucro Líquido Estimado Total: R$ %.2f", sumNet),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TrafficGreen
                                )
                                Text(
                                    text = String.format(
                                        Locale.forLanguageTag("pt-BR"),
                                        "Média Bruta das Ofertas: R$ %.2f/km  ·  R$ %.2f/h",
                                        avgGrossKm,
                                        avgGrossHr
                                    ),
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                                Text(
                                    text = "Aviso: Valores de custo e lucro são estimativas baseadas nos parâmetros configurados para o seu veículo, não representando lucro líquido garantido.",
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 9: DIAGNÓSTICO, INSPETOR E SIMULAÇÃO ISOLADA
                // =====================================================
                GoOrNoScreen.DIAGNOSTICS_TESTS -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SIMULADOR ISOLADO (NÃO MISTURA NO HISTÓRICO REAL)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Toque em qualquer botão moderno abaixo para testar a janelinha clean abrindo direto e sumindo após ${config.overlayAutoMinimizeSec}s:",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                // Botão 1: Exemplo de Referência Obrigatório da Seção 5.4 (R$ 18,00 / 8 km / 24 min)
                                Button(
                                    onClick = {
                                        val refLines = listOf(
                                            "UberX",
                                            "R$ 18,00",
                                            "2,0 km",
                                            "6,0 km",
                                            "6 min",
                                            "18 min",
                                            "Origem: Av. Paulista, 1578 - Bela Vista, São Paulo",
                                            "Destino: Rua Funchal, 418 - Vila Olímpia, São Paulo",
                                            "Aceitar"
                                        )
                                        val simOffer = UberOfferReader.parseScreenNodes(
                                            rawNodes = refLines,
                                            captureMethod = CaptureMethod.SIMULATION,
                                            isSimulated = true
                                        )
                                        val eval = FinancialEngine.evaluate(simOffer, config)
                                        RuntimeDiagnosticsBus.publishEvaluation(eval)
                                        if (hasOverlayPermission) {
                                            app.overlayPanel.applyConfig(config)
                                            app.overlayPanel.showEvaluation(eval)
                                        }
                                        notifyUser("Simulação Referência (R$ 18 / 8 km / 24 min -> R$ 2,25/km e R$ 45/h) exibida por ${config.overlayAutoMinimizeSec}s!")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LimeBright)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Testar Corrida UberX (R$ 18,00 · 8 km · 24 min)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }

                                // Botão 2: Simular Lista da 99 com 2 Ofertas Simultâneas na mesma tela!
                                Button(
                                    onClick = {
                                        val multi99Nodes = listOf(
                                            "Pgto. no app",
                                            "R$8,10 ⚡1,1x",
                                            "R$2,18/km",
                                            "R$1,16 por deslocamento incl.",
                                            "5 min (1,2 km)",
                                            "Rua Antônio Zanetti, 84, Jardim Las Vegas",
                                            "6 min (2,5 km)",
                                            "Rua Solimões, 534, Vila Cecilia Maria",
                                            "Pgto. no app",
                                            "R$14,20",
                                            "R$1,42/km",
                                            "8 min (2,5 km)",
                                            "Av. Goiás, 1200, São Caetano do Sul",
                                            "19 min (7,5 km)",
                                            "Terminal Sacomã, São Paulo"
                                        )
                                        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(
                                            rawNodes = multi99Nodes,
                                            captureMethod = CaptureMethod.SIMULATION,
                                            isSimulated = true
                                        )
                                        val evals = offers.map { FinancialEngine.evaluate(it, config) }
                                        if (evals.isNotEmpty()) {
                                            RuntimeDiagnosticsBus.publishEvaluation(evals.first())
                                            if (hasOverlayPermission) {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showEvaluations(evals)
                                            }
                                            notifyUser("Lidas ${evals.size} viagens simultâneas da 99 na janela flutuante!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TrafficGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ViewAgenda, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Testar 99 com 2 Viagens Simultâneas na Tela",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val lines99 = listOf(
                                                "99Pop",
                                                "R$ 14,50",
                                                "1,5 km",
                                                "7,5 km",
                                                "5 min",
                                                "22 min",
                                                "Embarque: Rua da Consolação, 2200 - São Paulo",
                                                "Destino: Terminal Barra Funda - São Paulo",
                                                "Aceitar corrida"
                                            )
                                            val offer = NinetyNineOfferReader.parseScreenNodes(
                                                rawNodes = lines99,
                                                captureMethod = CaptureMethod.SIMULATION,
                                                isSimulated = true
                                            )
                                            val eval = FinancialEngine.evaluate(offer, config)
                                            RuntimeDiagnosticsBus.publishEvaluation(eval)
                                            if (hasOverlayPermission) {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showEvaluation(eval)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TrafficYellow,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Simular 99 (Média)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val badUber = listOf(
                                                "UberX",
                                                "R$ 9,80",
                                                "3,2 km",
                                                "6,8 km",
                                                "9 min",
                                                "21 min",
                                                "Origem: Av. Brasil, 4500 - Rio de Janeiro",
                                                "Destino: Estrada do Galeão, 1200 - Rio de Janeiro",
                                                "Aceitar"
                                            )
                                            val offer = UberOfferReader.parseScreenNodes(
                                                rawNodes = badUber,
                                                captureMethod = CaptureMethod.SIMULATION,
                                                isSimulated = true
                                            )
                                            val eval = FinancialEngine.evaluate(offer, config)
                                            RuntimeDiagnosticsBus.publishEvaluation(eval)
                                            if (hasOverlayPermission) {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showEvaluation(eval)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TrafficRed,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Simular Uber (Ruim)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Exibe resultado da simulação isolada
                                if (lastSimulatedEvaluation != null) {
                                    HorizontalDivider(color = CleanCardBorder)
                                    Text(
                                        text = "RESULTADO DA SIMULAÇÃO DE TESTE:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = PetrolHeader
                                    )
                                    EvaluationDetailCardContent(
                                        result = lastSimulatedEvaluation!!,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) }
                                    )
                                }
                            }
                        }
                    }

                    // Inspetor de Elementos da Árvore de Acessibilidade
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "INSPETOR DE LEITURA DA ÁRVORE DE ACESSIBILIDADE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Pacote lido: ${lastSnapshot.packageName} (${lastSnapshot.nodeCount} nós visíveis)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PetrolDarkText
                                )
                                if (lastSnapshot.extractedTexts.isEmpty()) {
                                    Text(
                                        text = "Nenhum texto capturado ainda. Ative a Acessibilidade e abra a Uber Driver ou 99 Motorista para inspecionar quais textos elas disponibilizam.",
                                        fontSize = 11.5.sp,
                                        color = MutedText
                                    )
                                } else {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            lastSnapshot.extractedTexts.take(25).forEachIndexed { idx, line ->
                                                Text(
                                                    text = "[${idx + 1}] $line",
                                                    fontSize = 11.sp,
                                                    color = PetrolDarkText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 10: PRIVACIDADE, PERMISSÕES E TROCA DE APARELHO
                // =====================================================
                GoOrNoScreen.PRIVACY_BACKUP -> {
                    item {
                        var recoveryCode by remember(config.driverRecoveryCode) {
                            mutableStateOf(config.driverRecoveryCode)
                        }
                        var consentChecked by remember(config.cloudBackupConsent) {
                            mutableStateOf(config.cloudBackupConsent)
                        }
                        var jsonBackupBox by remember { mutableStateOf("") }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Troca de Aparelho — Backup & Recuperação na Nuvem",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Se você trocar de celular, basta informar seu Código de Motorista abaixo para salvar ou restaurar todas as suas metas, custos do veículo e histórico de corridas.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = consentChecked,
                                        onCheckedChange = {
                                            consentChecked = it
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(
                                                    config.copy(cloudBackupConsent = it)
                                                )
                                            }
                                        }
                                    )
                                    Text(
                                        text = "Autorizo sincronizar minhas configurações e histórico na nuvem com meu código",
                                        fontSize = 12.sp,
                                        color = PetrolDarkText
                                    )
                                }

                                OutlinedTextField(
                                    value = recoveryCode,
                                    onValueChange = { recoveryCode = it },
                                    label = { Text("Seu Código de Motorista (ex: murilo2026 ou telefone)") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (!consentChecked) {
                                                notifyUser("Marque a caixa de autorização acima antes de enviar para a nuvem.")
                                                return@Button
                                            }
                                            scope.launch {
                                                val savedCfg = config.copy(
                                                    driverRecoveryCode = recoveryCode.trim(),
                                                    cloudBackupConsent = true
                                                )
                                                app.preferencesRepository.saveConfig(savedCfg)
                                                val res = CloudSyncManager.uploadBackupByDriverCode(
                                                    driverCode = recoveryCode,
                                                    config = savedCfg,
                                                    offers = realHistory
                                                )
                                                notifyUser(res.getOrElse { "Erro: ${it.message}" })
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PetrolHeader,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Salvar na Nuvem", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val res = CloudSyncManager.restoreBackupByDriverCode(recoveryCode)
                                                res.onSuccess { (restoredCfg, restoredOffers) ->
                                                    app.preferencesRepository.saveConfig(restoredCfg)
                                                    app.database.rideOfferDao().insertAll(restoredOffers)
                                                    notifyUser("Sucesso! Configurações e ${restoredOffers.size} corridas restauradas para este aparelho!")
                                                }.onFailure {
                                                    notifyUser("Erro ao restaurar: ${it.message}")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SoftSurfaceVariant,
                                            contentColor = PetrolHeader
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Restaurar Dados", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Backup Local (Exportar / Importar JSON)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PetrolHeader
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            jsonBackupBox = CloudSyncManager.exportToJsonString(config, realHistory)
                                            notifyUser("JSON gerado abaixo! Você pode copiar ou compartilhar.")
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SoftSurfaceVariant,
                                            contentColor = PetrolHeader
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Gerar Backup JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    if (jsonBackupBox.isNotBlank()) {
                                        Button(
                                            onClick = {
                                                val parsed = CloudSyncManager.importFromJsonString(jsonBackupBox)
                                                if (parsed != null) {
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(parsed.first)
                                                        app.database.rideOfferDao().insertAll(parsed.second)
                                                        notifyUser("Backup JSON importado com sucesso!")
                                                    }
                                                } else {
                                                    notifyUser("JSON inválido.")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PetrolHeader,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Importar JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (jsonBackupBox.isNotBlank()) {
                                    OutlinedTextField(
                                        value = jsonBackupBox,
                                        onValueChange = { jsonBackupBox = it },
                                        label = { Text("Código JSON de Backup") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                    )
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Política de Privacidade e Transparência (Seção 9)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "• Finalidade da Acessibilidade e OCR: Usados exclusivamente para ler valor (R$), distância (km), tempo (min) e endereços das ofertas na tela da Uber Driver e 99 Motorista.\n" +
                                        "• Sem Cliques Automáticos: O GoOrNo jamais clica, aceita ou recusa viagens por você.\n" +
                                        "• Privacidade de Capturas de Tela: O OCR processa a imagem em memória via ML Kit local e descarta o bitmap imediatamente, sem armazenar prints.\n" +
                                        "• Zero Credenciais: O GoOrNo nunca solicita senha ou login da sua conta Uber ou 99.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(
    number: String,
    title: String,
    subtitle: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Surface(
        color = if (granted) TrafficGreenSoft.copy(alpha = 0.55f) else SoftSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (granted) TrafficGreen else TrafficYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (granted) TrafficGreen else TrafficYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (granted) "✓" else number,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = PetrolDarkText)
                    Text(text = subtitle, fontSize = 11.sp, color = MutedText)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) TrafficGreen else PetrolHeader,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = if (granted) "Ativo ✓" else "Ativar",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun EvaluationDetailCardContent(
    result: FinancialEvaluationResult,
    onOpenStreetView: (String) -> Unit
) {
    val verdictColor = when (result.verdict) {
        TrafficLightVerdict.GREEN -> TrafficGreen
        TrafficLightVerdict.YELLOW -> TrafficYellow
        TrafficLightVerdict.RED -> TrafficRed
        TrafficLightVerdict.GRAY_NEUTRAL -> TrafficGray
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = verdictColor,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "${result.verdict.label} — ${result.offer.sourceApp.displayName} (${result.offer.rideCategory ?: "Corrida"})",
                fontWeight = FontWeight.Black,
                fontSize = 12.5.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "OFERTA",
                value = result.offer.offeredPrice?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "BRUTO / KM",
                value = result.grossPerKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "BRUTO / HORA",
                value = result.grossPerHour?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "CUSTO EST.",
                value = result.estimatedTotalCost?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "LUCRO LÍQ.",
                value = result.estimatedNetProfit?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                label = "LUCRO / KM",
                value = result.netProfitPerKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
        }

        result.reasons.forEach { r ->
            Text(text = "• $r", fontSize = 11.5.sp, color = PetrolDarkText, fontWeight = FontWeight.Medium)
        }
        result.warnings.forEach { w ->
            Text(text = "⚠️ $w", fontSize = 11.5.sp, color = TrafficYellow, fontWeight = FontWeight.SemiBold)
        }

        // Botões Pílula Modernos 1-Toque Street View (Origem e Destino)
        if (!result.offer.originAddress.isNullOrBlank()) {
            Button(
                onClick = { onOpenStreetView(result.offer.originAddress!!) },
                colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = LimeBright, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Street View Origem: ${result.offer.originAddress}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!result.offer.destinationAddress.isNullOrBlank()) {
            FilledTonalButton(
                onClick = { onOpenStreetView(result.offer.destinationAddress!!) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = TrafficGreenSoft,
                    contentColor = PetrolHeader
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = TrafficGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Street View Destino: ${result.offer.destinationAddress}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HistoricalOfferCardContent(
    entity: RideOfferEntity,
    onOpenStreetView: (String) -> Unit,
    onUpdateReceivedPrice: (Double) -> Unit
) {
    var showAuditInput by remember { mutableStateOf(false) }
    var paidText by remember { mutableStateOf(entity.actualReceivedPrice?.toString() ?: entity.offeredPrice.toString()) }
    val dateStr = remember(entity.timestampMillis) {
        SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(entity.timestampMillis))
    }

    val badgeColor = when (entity.verdict) {
        TrafficLightVerdict.GREEN.name -> TrafficGreen
        TrafficLightVerdict.YELLOW.name -> TrafficYellow
        TrafficLightVerdict.RED.name -> TrafficRed
        else -> TrafficGray
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "${entity.sourceApp} · R$ ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", entity.offeredPrice)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(text = dateStr, fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.Medium)
        }

        Text(
            text = String.format(
                Locale.forLanguageTag("pt-BR"),
                "Bruto: R$ %.2f/km · R$ %.2f/h  |  Lucro Est.: R$ %.2f",
                entity.grossPerKm ?: 0.0,
                entity.grossPerHour ?: 0.0,
                entity.estimatedNetProfit ?: 0.0
            ),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = PetrolHeader
        )

        // Auditoria de Valor Pago pela Uber / 99
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entity.actualReceivedPrice != null) {
                val diff = entity.actualReceivedPrice - entity.offeredPrice
                Text(
                    text = String.format(
                        Locale.forLanguageTag("pt-BR"),
                        "Pago no Fim: R$ %.2f (%s)",
                        entity.actualReceivedPrice,
                        if (diff < -0.05) "Pagou a menor!" else "Valor OK ✓"
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (diff < -0.05) TrafficRed else TrafficGreen
                )
            } else {
                Text(
                    text = "Valor pago no final ainda não conferido",
                    fontSize = 11.sp,
                    color = MutedText
                )
            }
            TextButton(onClick = { showAuditInput = !showAuditInput }) {
                Text("Conferir Valor Pago", color = PetrolHeader, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showAuditInput) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it.replace(",", ".") },
                    label = { Text("Valor Pago (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val v = paidText.toDoubleOrNull()
                        if (v != null && v >= 0.0) {
                            onUpdateReceivedPrice(v)
                            showAuditInput = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Black)
                }
            }
        }

        // Botões Modernos de Origem e Destino no Street View
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!entity.originAddress.isNullOrBlank()) {
                FilledTonalButton(
                    onClick = { onOpenStreetView(entity.originAddress) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftSurfaceVariant,
                        contentColor = PetrolHeader
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "📍 Origem (360°)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            if (!entity.destinationAddress.isNullOrBlank()) {
                FilledTonalButton(
                    onClick = { onOpenStreetView(entity.destinationAddress) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = TrafficGreenSoft,
                        contentColor = PetrolHeader
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🏁 Destino (360°)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = SoftSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CleanCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 10.sp, color = MutedText, fontWeight = FontWeight.Bold)
            Text(text = value, fontSize = 13.5.sp, color = PetrolDarkText, fontWeight = FontWeight.Black)
        }
    }
}

private fun openStreetViewExternal(context: Context, address: String) {
    try {
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
