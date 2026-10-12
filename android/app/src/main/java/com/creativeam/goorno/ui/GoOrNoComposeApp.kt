package com.creativeam.goorno.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
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
 * As 10 Telas Oficiais do GoOrNo exigidas na Seção 8.
 */
enum class GoOrNoScreen(val stepNumber: Int, val title: String, val shortTab: String) {
    HOME(1, "Tela Inicial — Copiloto", "Início"),
    DRIVER_SETUP(2, "Configuração do Motorista & Painel", "Motorista"),
    VEHICLE_COSTS(3, "Veículo e Custos Operacionais", "Custos"),
    FINANCIAL_TARGETS(4, "Metas Financeiras e Semáforo", "Metas"),
    SERVICES_ACTIVATION(5, "Ativação dos Serviços de Leitura", "Serviços"),
    CONNECTION_STATUS(6, "Status de Conexão Uber e 99", "Uber / 99"),
    HISTORY(7, "Histórico e Auditoria de Chamadas", "Histórico"),
    FINANCIAL_SUMMARY(8, "Resumo Financeiro", "Resumo"),
    DIAGNOSTICS_TESTS(9, "Diagnóstico, OCR e Simulação", "Diagnóstico"),
    PRIVACY_BACKUP(10, "Privacidade, Permissões e Troca de Aparelho", "Privacidade")
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
    val overlayVisible by RuntimeDiagnosticsBus.overlayVisible.collectAsState()
    val lastSnapshot by RuntimeDiagnosticsBus.lastSnapshot.collectAsState()
    val lastRealEvaluation by RuntimeDiagnosticsBus.lastRealEvaluation.collectAsState()
    val lastSimulatedEvaluation by RuntimeDiagnosticsBus.lastSimulatedEvaluation.collectAsState()

    var currentScreen by remember { mutableStateOf(GoOrNoScreen.HOME) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val installedApps = remember(currentScreen) {
        ForegroundAppDetector.checkInstalledRideApps(context)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PetrolSurface)
            ) {
                // Header Principal com Logo Oficial e Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_goorno),
                            contentDescription = "Logo GoOrNo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PetrolDarkBg)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "GoOrNo",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = LimePrimary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "COPILOTO",
                                        color = PetrolDarkBg,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = runtimeState.label,
                                fontSize = 11.sp,
                                color = if (accessibilityConnected || ocrActive) LimeBright else Color(0xFFA7F3D0)
                            )
                        }
                    }

                    // Botão Rápido Painel Flutuante + Transparência
                    OutlinedButton(
                        onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP },
                        border = BorderStroke(1.dp, LimePrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = LimeBright,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${config.overlayOpacityPercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Seletor Horizontal das 10 Telas (Acesso rápido em 1 toque a qualquer módulo)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GoOrNoScreen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        Surface(
                            color = if (selected) LimePrimary else PetrolDarkBg,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (selected) Color.White else PetrolCardBorder),
                            modifier = Modifier.clickable { currentScreen = screen }
                        ) {
                            Text(
                                text = "${screen.stepNumber}. ${screen.shortTab}",
                                color = if (selected) PetrolDarkBg else Color.White,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = PetrolSurface,
                tonalElevation = 8.dp
            ) {
                val quickTabs = listOf(
                    Triple(GoOrNoScreen.HOME, Icons.Default.PowerSettingsNew, "Início"),
                    Triple(GoOrNoScreen.SERVICES_ACTIVATION, Icons.Default.Layers, "Flutuante"),
                    Triple(GoOrNoScreen.FINANCIAL_TARGETS, Icons.Default.Tune, "Metas"),
                    Triple(GoOrNoScreen.HISTORY, Icons.Default.History, "Histórico"),
                    Triple(GoOrNoScreen.DIAGNOSTICS_TESTS, Icons.Default.Science, "Testes")
                )
                quickTabs.forEach { (screen, icon, label) ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PetrolDarkBg,
                            selectedTextColor = LimeBright,
                            indicatorColor = LimePrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PetrolDarkBg)
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
                    color = LimeBright
                )
            }

            if (statusMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E4F41)),
                        border = BorderStroke(1.dp, LimeBright)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusMessage!!,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { statusMessage = null }) {
                                Text("OK", color = LimeBright, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            when (currentScreen) {
                // =====================================================
                // TELA 1: TELA INICIAL (COPILOTO & ÚLTIMA OFERTA REAL)
                // =====================================================
                GoOrNoScreen.HOME -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (hasOverlayPermission && (hasAccessibilityPermission || ocrActive))
                                                "COPILOTO PRONTO PARA UBER E 99"
                                            else
                                                "ATIVE AS PERMISSÕES PARA INICIAR",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Abre uma janela flutuante transparente sobre a Uber e a 99 calculando R$/km, R$/hora e lucro líquido.",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Button(
                                        onClick = {
                                            if (!hasOverlayPermission) {
                                                onRequestOverlayPermission()
                                            } else if (!hasAccessibilityPermission) {
                                                onRequestAccessibilityPermission()
                                            } else {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showStandbyPanel()
                                                statusMessage = "Painel flutuante aberto sobre a tela!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LimePrimary,
                                            contentColor = PetrolDarkBg
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (overlayVisible) "Painel Ativo" else "Ligar Painel",
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // Checklist Rápido das 2 Permissões Essenciais
                                PermissionRowItem(
                                    number = "1",
                                    title = "Janela Flutuante (Sobrepor a outros apps)",
                                    subtitle = "Exibe o semáforo transparente sobre a Uber/99",
                                    granted = hasOverlayPermission,
                                    onAction = onRequestOverlayPermission
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PermissionRowItem(
                                    number = "2",
                                    title = "Leitor de Ofertas (Acessibilidade GoOrNo)",
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ÚLTIMA OFERTA REAL DETECTADA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Spacer(modifier = Modifier.height(6.dp))

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
                                                statusMessage = "Valor recebido conferido e salvo!"
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "Nenhuma oferta real da Uber ou 99 foi capturada ainda nesta sessão. Assim que uma chamada tocar na Uber Driver ou 99 Motorista, ela aparecerá aqui e no painel flutuante.",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { currentScreen = GoOrNoScreen.DIAGNOSTICS_TESTS },
                                        border = BorderStroke(1.dp, LimePrimary)
                                    ) {
                                        Text(
                                            text = "Ir para Tela de Diagnóstico e Simulação Isolada",
                                            color = LimeBright,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
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

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = driverName,
                                    onValueChange = { driverName = it },
                                    label = { Text("Nome ou Apelido do Motorista") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = vehicleModel,
                                    onValueChange = { vehicleModel = it },
                                    label = { Text("Modelo do Veículo (ex: Onix 1.0, HB20, Kwid)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "Transparência do Painel Flutuante: ${opacity.toInt()}%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Ajuste a opacidade para enxergar o mapa da Uber e da 99 por baixo do card sem bloquear a visão.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )

                                Slider(
                                    value = opacity,
                                    onValueChange = {
                                        opacity = it
                                        val updated = config.copy(overlayOpacityPercent = it.toInt())
                                        app.overlayPanel.applyConfig(updated)
                                    },
                                    valueRange = 25f..100f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(
                                        thumbColor = LimeBright,
                                        activeTrackColor = LimePrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = autoMinSec,
                                    onValueChange = { autoMinSec = it },
                                    label = { Text("Tempo aberto antes de minimizar sozinho (segundos)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            driverName = driverName.ifBlank { "Motorista" },
                                            vehicleModel = vehicleModel.ifBlank { "Veículo" },
                                            overlayOpacityPercent = opacity.toInt().coerceIn(25, 100),
                                            overlayAutoMinimizeSec = (autoMinSec.toIntOrNull() ?: 12).coerceIn(5, 60)
                                        )
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            app.overlayPanel.applyConfig(updated)
                                            statusMessage = "Configurações de perfil e transparência salvas!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimePrimary,
                                        contentColor = PetrolDarkBg
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Salvar Configuração do Motorista", fontWeight = FontWeight.Black)
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

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = String.format(
                                        Locale.forLanguageTag("pt-BR"),
                                        "Custo Operacional Atual: R$ %.2f / km (Combustível: R$ %.2f/km + Manutenção: R$ %.2f/km)",
                                        config.totalVariableCostPerKm,
                                        config.fuelCostPerKm,
                                        config.maintenanceCostPerKm
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = LimeBright
                                )

                                OutlinedTextField(
                                    value = kmPerLiter,
                                    onValueChange = { kmPerLiter = it.replace(",", ".") },
                                    label = { Text("Consumo médio do veículo (km/l)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = fuelPrice,
                                    onValueChange = { fuelPrice = it.replace(",", ".") },
                                    label = { Text("Preço do combustível por litro (R$/l)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = maintPerKm,
                                    onValueChange = { maintPerKm = it.replace(",", ".") },
                                    label = { Text("Custo de manutenção, pneus e desgaste por km (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = extraPerRide,
                                    onValueChange = { extraPerRide = it.replace(",", ".") },
                                    label = { Text("Custo fixo adicional por corrida (R$) — opcional") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text(
                                    text = "Nota: Os custos são estimativas operacionais para separar claramente Receita Bruta de Lucro Líquido Estimado, sem contabilizar duas vezes o mesmo item.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
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
                                            statusMessage = "Custos do veículo atualizados com sucesso!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimePrimary,
                                        contentColor = PetrolDarkBg
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Salvar Custos Operacionais", fontWeight = FontWeight.Black)
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

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Defina os valores mínimos para o Semáforo classificar uma corrida como VERDE e escolha quais metas são obrigatórias:",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )

                                OutlinedTextField(
                                    value = grossKm,
                                    onValueChange = { grossKm = it.replace(",", ".") },
                                    label = { Text("Meta Mínima de Ganho Bruto por KM (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqGrossKm, onCheckedChange = { reqGrossKm = it })
                                    Text("Exigir Ganho Bruto/km na classificação", fontSize = 12.sp)
                                }

                                OutlinedTextField(
                                    value = grossHr,
                                    onValueChange = { grossHr = it.replace(",", ".") },
                                    label = { Text("Meta Mínima de Ganho Bruto por Hora (R$/h)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqGrossHr, onCheckedChange = { reqGrossHr = it })
                                    Text("Exigir Ganho Bruto/hora na classificação", fontSize = 12.sp)
                                }

                                OutlinedTextField(
                                    value = netKm,
                                    onValueChange = { netKm = it.replace(",", ".") },
                                    label = { Text("Meta de Lucro Líquido por KM (R$/km)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqNetKm, onCheckedChange = { reqNetKm = it })
                                    Text("Exigir Lucro Líquido/km na classificação", fontSize = 12.sp)
                                }

                                OutlinedTextField(
                                    value = netHr,
                                    onValueChange = { netHr = it.replace(",", ".") },
                                    label = { Text("Meta de Lucro Líquido por Hora (R$/h)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = reqNetHr, onCheckedChange = { reqNetHr = it })
                                    Text("Exigir Lucro Líquido/hora na classificação", fontSize = 12.sp)
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
                                            statusMessage = "Metas financeiras e critérios do semáforo salvos!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimePrimary,
                                        contentColor = PetrolDarkBg
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Salvar Metas Financeiras", fontWeight = FontWeight.Black)
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
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. Permissão de Sobreposição (Painel Flutuante)",
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Text(
                                    text = if (hasOverlayPermission) "Status: CONCEDIDA ✓" else "Status: PENDENTE ⚠️",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onRequestOverlayPermission,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LimePrimary,
                                            contentColor = PetrolDarkBg
                                        )
                                    ) {
                                        Text("Configurar Sobreposição", fontWeight = FontWeight.Bold)
                                    }
                                    if (hasOverlayPermission) {
                                        OutlinedButton(
                                            onClick = {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showStandbyPanel()
                                            },
                                            border = BorderStroke(1.dp, LimePrimary)
                                        ) {
                                            Text("Abrir Painel", color = LimeBright)
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "2. Método Principal: Serviço de Acessibilidade",
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Text(
                                    text = if (hasAccessibilityPermission)
                                        "Status: ATIVO E MONITORANDO ✓"
                                    else
                                        "Status: DESATIVADO (Toque abaixo para habilitar nas Configurações do Android)",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Button(
                                    onClick = onRequestAccessibilityPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimePrimary,
                                        contentColor = PetrolDarkBg
                                    )
                                ) {
                                    Text("Abrir Configurações de Acessibilidade", fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "3. Método Alternativo: Leitura OCR por Captura de Tela (ML Kit Local)",
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Text(
                                    text = if (ocrActive)
                                        "Status OCR: ATIVO EM SEGUNDO PLANO ✓"
                                    else
                                        "Status OCR: DESLIGADO (Ative caso alguma oferta da Uber/99 não exponha texto na Acessibilidade)",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Button(
                                    onClick = onRequestOcrCapturePermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0D4A3E),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(if (ocrActive) "Reiniciar Permissão OCR" else "Autorizar Captura OCR (MediaProjection)")
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                // Botão claro para encerrar todos os monitoramentos em segundo plano (Seção 9)
                                Button(
                                    onClick = {
                                        onStopAllBackgroundServices()
                                        statusMessage = "Painel flutuante e captura OCR encerrados com sucesso."
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TrafficRed,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.StopCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desativar Monitoramento e Fechar Painel Flutuante", fontWeight = FontWeight.Black)
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Verificação Real dos Pacotes no Aparelho",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = LimeBright
                                )

                                // Uber Driver Status
                                Surface(
                                    color = PetrolDarkBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PetrolCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Uber Driver (Motorista)",
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (installedApps.uberInstalled)
                                                "Instalado no aparelho ✓ (Pacote: ${installedApps.uberPackageFound})"
                                            else
                                                "Não detectado neste aparelho (Pacote esperado: com.ubercab.driver)",
                                            fontSize = 12.sp,
                                            color = if (installedApps.uberInstalled) Color(0xFF6EE7B7) else Color(0xFFFCD34D)
                                        )
                                    }
                                }

                                // 99 Motorista Status
                                Surface(
                                    color = PetrolDarkBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PetrolCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "99 Motorista",
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (installedApps.ninetyNineInstalled)
                                                "Instalado no aparelho ✓ (Pacote: ${installedApps.ninetyNinePackageFound})"
                                            else
                                                "Não detectado neste aparelho (Pacotes: com.taxis99.driver / com.didiglobal.driver)",
                                            fontSize = 12.sp,
                                            color = if (installedApps.ninetyNineInstalled) Color(0xFF6EE7B7) else Color(0xFFFCD34D)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "Última Janela Detectada em Primeiro Plano:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Pacote: ${lastSnapshot.packageName}\nApp Identificado: ${lastSnapshot.detectedApp.displayName}\nElementos de texto lidos: ${lastSnapshot.nodeCount}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
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
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (realHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            app.database.rideOfferDao().clearAllRealOffers()
                                            statusMessage = "Histórico local limpo."
                                        }
                                    }
                                ) {
                                    Text("Limpar Histórico", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (realHistory.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                                border = BorderStroke(1.dp, PetrolCardBorder)
                            ) {
                                Text(
                                    text = "Nenhuma oferta real gravada ainda. Nota importante: Testes feitos no Modo de Simulação nunca são misturados neste histórico real.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(realHistory, key = { it.id }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                                border = BorderStroke(1.dp, PetrolCardBorder),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    HistoricalOfferCardContent(
                                        entity = item,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) },
                                        onUpdateReceivedPrice = { valPaid ->
                                            scope.launch {
                                                app.database.rideOfferDao().updateActualReceivedPrice(item.id, valPaid)
                                                statusMessage = "Valor recebido conferido com sucesso!"
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Consolidado das Ofertas Reais Analisadas",
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Ofertas Analisadas: $totalOffers  (🟢 $greenCount  |  🟡 $yellowCount  |  🔴 $redCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Receita Bruta Ofertada Total: R$ %.2f", sumGross),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Custo Operacional Estimado Total: R$ %.2f", sumCost),
                                    fontSize = 13.sp,
                                    color = Color(0xFFFCD34D)
                                )
                                Text(
                                    text = String.format(Locale.forLanguageTag("pt-BR"), "Lucro Líquido Estimado Total: R$ %.2f", sumNet),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = LimeBright
                                )
                                Text(
                                    text = String.format(
                                        Locale.forLanguageTag("pt-BR"),
                                        "Média Bruta das Ofertas: R$ %.2f/km  ·  R$ %.2f/h",
                                        avgGrossKm,
                                        avgGrossHr
                                    ),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "Aviso: Valores de custo e lucro são estimativas baseadas nos parâmetros configurados para o seu veículo, não representando lucro líquido garantido.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.65f)
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, LimePrimary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "MODO DE SIMULAÇÃO ISOLADO (NÃO GRAVA NO HISTÓRICO REAL)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Dispare ofertas de teste abaixo para validar os leitores da Uber e da 99, o Motor Financeiro, o Semáforo e o Painel Flutuante:",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
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
                                        statusMessage = "Simulação Referência (R$ 18 / 8 km / 24 min -> R$ 2,25/km e R$ 45/h) executada!"
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LimePrimary,
                                        contentColor = PetrolDarkBg
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Testar Referência Seção 5.4 (R$ 18,00 | 8 km | 24 min)",
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
                                        colors = ButtonDefaults.buttonColors(containerColor = TrafficYellow),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Simular 99Pop (Média)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = TrafficRed),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Simular Uber (Ruim)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Exibe resultado da simulação isolada
                                if (lastSimulatedEvaluation != null) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                    Text(
                                        text = "RESULTADO DA SIMULAÇÃO DE TESTE:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = LimeBright
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "INSPETOR DE LEITURA DA ÁRVORE DE ACESSIBILIDADE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Pacote lido: ${lastSnapshot.packageName} (${lastSnapshot.nodeCount} nós visíveis)",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                if (lastSnapshot.extractedTexts.isEmpty()) {
                                    Text(
                                        text = "Nenhum texto capturado ainda. Ative a Acessibilidade e abra a Uber Driver ou 99 Motorista para inspecionar quais textos elas disponibilizam.",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Surface(
                                        color = PetrolDarkBg,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            lastSnapshot.extractedTexts.take(25).forEachIndexed { idx, line ->
                                                Text(
                                                    text = "[${idx + 1}] $line",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFD1FAE5)
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
                            colors = CardDefaults.cardColors(containerColor = PetrolSurface),
                            border = BorderStroke(1.dp, PetrolCardBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Troca de Aparelho — Backup & Recuperação na Nuvem",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "Se você trocar de celular, basta informar seu Código de Motorista abaixo para salvar ou restaurar todas as suas metas, custos do veículo e histórico de corridas.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
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
                                        fontSize = 12.sp
                                    )
                                }

                                OutlinedTextField(
                                    value = recoveryCode,
                                    onValueChange = { recoveryCode = it },
                                    label = { Text("Seu Código de Motorista (ex: murilo2026 ou telefone)") },
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
                                                statusMessage = "Marque a caixa de autorização acima antes de enviar para a nuvem."
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
                                                statusMessage = res.getOrElse { "Erro: ${it.message}" }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LimePrimary,
                                            contentColor = PetrolDarkBg
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Salvar na Nuvem", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = CloudSyncManager.restoreBackupByDriverCode(recoveryCode)
                                                res.onSuccess { (restoredCfg, restoredOffers) ->
                                                    app.preferencesRepository.saveConfig(restoredCfg)
                                                    app.database.rideOfferDao().insertAll(restoredOffers)
                                                    statusMessage =
                                                        "Sucesso! Configurações e ${restoredOffers.size} corridas restauradas para este aparelho!"
                                                }.onFailure {
                                                    statusMessage = "Erro ao restaurar: ${it.message}"
                                                }
                                            }
                                        },
                                        border = BorderStroke(1.dp, LimeBright),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Restaurar Dados", color = LimeBright, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "Backup Local (Exportar / Importar JSON via WhatsApp)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = LimeBright
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            jsonBackupBox = CloudSyncManager.exportToJsonString(config, realHistory)
                                            statusMessage = "JSON gerado abaixo! Você pode copiar ou compartilhar."
                                        },
                                        border = BorderStroke(1.dp, LimePrimary)
                                    ) {
                                        Text("Gerar Backup JSON", color = Color.White, fontSize = 12.sp)
                                    }
                                    if (jsonBackupBox.isNotBlank()) {
                                        Button(
                                            onClick = {
                                                val parsed = CloudSyncManager.importFromJsonString(jsonBackupBox)
                                                if (parsed != null) {
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(parsed.first)
                                                        app.database.rideOfferDao().insertAll(parsed.second)
                                                        statusMessage = "Backup JSON importado com sucesso!"
                                                    }
                                                } else {
                                                    statusMessage = "JSON inválido."
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = LimePrimary,
                                                contentColor = PetrolDarkBg
                                            )
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                    )
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Text(
                                    text = "Política de Privacidade e Transparência (Seção 9)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = LimeBright
                                )
                                Text(
                                    text = "• Finalidade da Acessibilidade e OCR: Usados exclusivamente para ler valor (R$), distância (km), tempo (min) e endereços das ofertas na tela da Uber Driver e 99 Motorista.\n" +
                                        "• Sem Cliques Automáticos: O GoOrNo jamais clica, aceita ou recusa viagens por você.\n" +
                                        "• Privacidade de Capturas de Tela: O OCR processa a imagem em memória via ML Kit local e descarta o bitmap imediatamente, sem armazenar prints.\n" +
                                        "• Zero Credenciais: O GoOrNo nunca solicita senha ou login da sua conta Uber ou 99.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
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
        color = PetrolDarkBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (granted) TrafficGreen else TrafficYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
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
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    Text(text = subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) Color(0xFF0D4A3E) else LimePrimary,
                    contentColor = if (granted) LimeBright else PetrolDarkBg
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (granted) "Ativo" else "Ativar",
                    fontSize = 11.sp,
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
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${result.verdict.label} — ${result.offer.sourceApp.displayName} (${result.offer.rideCategory ?: "Corrida"})",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricBox(
                label = "OFERTA",
                value = result.offer.offeredPrice?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                label = "BRUTO / KM",
                value = result.grossPerKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                label = "BRUTO / HORA",
                value = result.grossPerHour?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricBox(
                label = "CUSTO EST.",
                value = result.estimatedTotalCost?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                label = "LUCRO LÍQ.",
                value = result.estimatedNetProfit?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
            MetricBox(
                label = "LUCRO / KM",
                value = result.netProfitPerKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", it) } ?: "--"
            )
        }

        result.reasons.forEach { r ->
            Text(text = "• $r", fontSize = 11.sp, color = Color(0xFFD1FAE5))
        }
        result.warnings.forEach { w ->
            Text(text = "⚠️ $w", fontSize = 11.sp, color = Color(0xFFFCD34D))
        }

        // Botões 1-Toque Street View (Origem e Destino)
        if (!result.offer.originAddress.isNullOrBlank()) {
            Button(
                onClick = { onOpenStreetView(result.offer.originAddress!!) },
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = PetrolDarkBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Abrir Origem no Street View: ${result.offer.originAddress}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!result.offer.destinationAddress.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onOpenStreetView(result.offer.destinationAddress!!) },
                border = BorderStroke(1.dp, LimeBright),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = LimeBright, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Abrir Destino no Street View: ${result.offer.destinationAddress}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
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
            Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = "${entity.sourceApp} · R$ ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", entity.offeredPrice)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(text = dateStr, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        }

        Text(
            text = String.format(
                Locale.forLanguageTag("pt-BR"),
                "Bruto: R$ %.2f/km · R$ %.2f/h  |  Lucro Est.: R$ %.2f",
                entity.grossPerKm ?: 0.0,
                entity.grossPerHour ?: 0.0,
                entity.estimatedNetProfit ?: 0.0
            ),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = LimeBright
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
                    color = if (diff < -0.05) Color(0xFFFCA5A5) else Color(0xFF6EE7B7)
                )
            } else {
                Text(
                    text = "Valor pago no final ainda não conferido",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            TextButton(onClick = { showAuditInput = !showAuditInput }) {
                Text("Conferir Valor Pago", color = LimeBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = PetrolDarkBg)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Black)
                }
            }
        }

        // Botões de Origem e Destino no Street View
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!entity.originAddress.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onOpenStreetView(entity.originAddress) },
                    border = BorderStroke(1.dp, LimePrimary),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "📍 Origem (360°)",
                        fontSize = 11.sp,
                        color = LimeBright,
                        maxLines = 1
                    )
                }
            }
            if (!entity.destinationAddress.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onOpenStreetView(entity.destinationAddress) },
                    border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🏁 Destino (360°)",
                        fontSize = 11.sp,
                        color = Color(0xFF6EE7B7),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String) {
    Surface(
        color = PetrolDarkBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PetrolCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Bold)
            Text(text = value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Black)
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
