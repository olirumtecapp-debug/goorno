package com.creativeam.goorno.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.creativeam.goorno.engine.RiskAreaEvaluator
import com.creativeam.goorno.engine.FavoriteAreaEvaluator
import com.creativeam.goorno.model.*
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import com.creativeam.goorno.service.CopilotNotificationReceiver
import com.creativeam.goorno.ui.theme.*
import com.creativeam.goorno.util.GeocodedPlace
import com.creativeam.goorno.util.PlaceSearchHelper
import com.creativeam.goorno.util.StreetViewLauncher
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Informações de versão remota para verificação automática de atualizações.
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val apkUrl: String,
    val directDownload: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val changelog: List<String>,
    val mandatory: Boolean
)

/**
 * Telas Oficiais do GoOrNo v3.1.0 — Interface Clean, Clara e Moderna.
 */
enum class GoOrNoScreen(val stepNumber: Int, val title: String, val shortTab: String) {
    HOME(1, "Copiloto & Status", "Início"),
    FINANCIAL_TARGETS(2, "Metas & Semáforo", "Ganhos"),
    DRIVER_SETUP(3, "Formato da Janela & Visual", "Janela"),
    HISTORY(4, "Histórico de Corridas", "Histórico"),
    SETTINGS(5, "Ajustes do Aplicativo", "Ajustes"),

    // Subtelas de Ajustes e Custos (mantidas para compatibilidade total):
    VEHICLE_COSTS(6, "Custos do Carro", "Custos"),
    SERVICES_ACTIVATION(7, "Permissões e Serviços", "Serviços"),
    CONNECTION_STATUS(8, "Conexão com Uber e 99", "Uber / 99"),
    SECURITY(9, "Segurança e Áreas de Risco", "Segurança"),
    DIAGNOSTICS_TESTS(10, "Diagnóstico e Simulador", "Testes"),
    PRIVACY_BACKUP(11, "Privacidade e Backup", "Backup"),
    SUBSCRIPTION(12, "Assinatura Pro e Licença", "Plano Pro")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoOrNoComposeApp(
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    initialRequestedScreen: String? = null,
    onClearRequestedScreen: () -> Unit = {},
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

    val currentAppVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.3.13"
        } catch (_: Exception) {
            "3.3.13"
        }
    }
    val currentAppVersionCode = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            34
        }
    }

    var updateInfoState by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    var showAdjustFaixasDialog by remember { mutableStateOf(false) }
    var pendingRiskAddressToConfirm by remember { mutableStateOf<String?>(null) }
    var pendingFavoriteAddressToConfirm by remember { mutableStateOf<String?>(null) }
    var securitySelectedTab by remember { mutableStateOf(0) }

    val promptMarkAddressAsRisk: (String) -> Unit = { rawAddr ->
        val cleanTerm = RiskAreaEvaluator.extractCleanStreetOrDistrict(rawAddr)
        if (cleanTerm.length >= 3) {
            pendingRiskAddressToConfirm = cleanTerm
        } else {
            Toast.makeText(context, "Endereço muito curto para cadastrar como risco.", Toast.LENGTH_SHORT).show()
        }
    }

    val promptMarkAddressAsFavorite: (String) -> Unit = { rawAddr ->
        val cleanTerm = FavoriteAreaEvaluator.extractCleanStreetOrDistrict(rawAddr)
        if (cleanTerm.length >= 3) {
            pendingFavoriteAddressToConfirm = cleanTerm
        } else {
            Toast.makeText(context, "Endereço muito curto para cadastrar como favorito.", Toast.LENGTH_SHORT).show()
        }
    }

    val checkForUpdates: (Boolean) -> Unit = { manual ->
        isCheckingUpdates = true
        scope.launch(Dispatchers.IO) {
            try {
                val conn = (URL("https://goorno-app.vercel.app/api/app-version").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val rCode = json.optInt("versionCode", 0)
                    val rName = json.optString("versionName", "")
                    val rDate = json.optString("releaseDate", "")
                    val rUrl = json.optString("apkUrl", "https://goorno-app.vercel.app/GoOrNo.apk")
                    val rDirect = json.optString("directDownload", "/GoOrNo.apk")
                    val rBytes = json.optLong("fileSizeBytes", 0L)
                    val rSizeFormatted = json.optString("fileSizeFormatted", "")
                    val rMandatory = json.optBoolean("mandatory", false)
                    val changelogList = mutableListOf<String>()
                    val clArray = json.optJSONArray("changelog")
                    if (clArray != null) {
                        for (i in 0 until clArray.length()) {
                            changelogList.add(clArray.optString(i))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        isCheckingUpdates = false
                        if (rCode > currentAppVersionCode) {
                            val info = AppUpdateInfo(
                                versionName = rName,
                                versionCode = rCode,
                                releaseDate = rDate,
                                apkUrl = rUrl,
                                directDownload = rDirect,
                                fileSizeBytes = rBytes,
                                fileSizeFormatted = rSizeFormatted,
                                changelog = changelogList,
                                mandatory = rMandatory
                            )
                            updateInfoState = info
                            showUpdateDialog = true
                        } else if (manual) {
                            Toast.makeText(context, "✅ Você já está na versão mais recente (v$currentAppVersionName)!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (manual) {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdates = false
                        Toast.makeText(context, "Não foi possível verificar no momento (código ${conn.responseCode}).", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdates = false
                        Toast.makeText(context, "Falha de conexão ao verificar atualizações: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdates = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdates(false)
    }

    LaunchedEffect(initialRequestedScreen) {
        if (!initialRequestedScreen.isNullOrBlank()) {
            when (initialRequestedScreen) {
                "DRIVER_SETUP", "JANELA", "ESTILO" -> currentScreen = GoOrNoScreen.DRIVER_SETUP
                "FINANCIAL_TARGETS", "SEMAFORO", "METAS", "GANHOS" -> currentScreen = GoOrNoScreen.FINANCIAL_TARGETS
                "SECURITY", "SEGURANCA" -> currentScreen = GoOrNoScreen.SECURITY
                "HISTORY", "HISTORICO" -> currentScreen = GoOrNoScreen.HISTORY
                "VEHICLE_COSTS", "CUSTOS" -> currentScreen = GoOrNoScreen.VEHICLE_COSTS
                "SETTINGS", "AJUSTES", "CONFIG" -> currentScreen = GoOrNoScreen.SETTINGS
                "SERVICES_ACTIVATION", "SERVICOS", "PERMISSOES" -> currentScreen = GoOrNoScreen.SERVICES_ACTIVATION
                "SUBSCRIPTION", "PRO", "PLANO" -> currentScreen = GoOrNoScreen.SUBSCRIPTION
                "DIAGNOSTICS_TESTS", "TESTES", "SIMULADOR" -> currentScreen = GoOrNoScreen.DIAGNOSTICS_TESTS
                "PRIVACY_BACKUP", "BACKUP" -> currentScreen = GoOrNoScreen.PRIVACY_BACKUP
                "HOME", "INICIO" -> currentScreen = GoOrNoScreen.HOME
                else -> {}
            }
            onClearRequestedScreen()
        }
    }

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
                    // Header Superior Verde-Petróleo Oficial com Logo Nítido e Estrutura Limpa
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PetrolHeader)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_goorno),
                                contentDescription = "Logo GoOrNo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .padding(3.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "GoOrNo",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    Surface(
                                        color = Color.White.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable { currentScreen = GoOrNoScreen.SUBSCRIPTION }
                                    ) {
                                        Text(
                                            text = "⭐ PRO",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        color = if (updateInfoState != null) TrafficYellow else LimeBright,
                                        shape = RoundedCornerShape(5.dp),
                                        modifier = Modifier.clickable {
                                            if (updateInfoState != null) {
                                                showUpdateDialog = true
                                            } else {
                                                checkForUpdates(true)
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (updateInfoState != null) "v$currentAppVersionName • UPDATE!" else "v$currentAppVersionName",
                                            color = PetrolDarkText,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                    Text(
                                        text = "•",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = runtimeState.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (accessibilityConnected || ocrActive) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Botão Pílula Moderno de Ajuste Rápido da Janela
                        FilledTonalButton(
                            onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = LimeBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${config.overlayAutoMinimizeSec}s · ${config.overlayOpacityPercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    val isSubScreenOfSettings = currentScreen in listOf(
                        GoOrNoScreen.SECURITY,
                        GoOrNoScreen.SERVICES_ACTIVATION,
                        GoOrNoScreen.CONNECTION_STATUS,
                        GoOrNoScreen.DIAGNOSTICS_TESTS,
                        GoOrNoScreen.PRIVACY_BACKUP,
                        GoOrNoScreen.SUBSCRIPTION
                    )

                    // Barra Superior Contextual: Se estiver em uma subtela de Ajustes, mostra atalho limpo de retorno
                    if (isSubScreenOfSettings) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PureWhiteSurface)
                                .clickable { currentScreen = GoOrNoScreen.SETTINGS }
                                .padding(start = 54.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = SoftSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Voltar",
                                        tint = PetrolHeader,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Voltar para Ajustes",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PetrolHeader,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                color = PetrolHeader.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = currentScreen.shortTab,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = CleanCardBorder)
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

                Surface(
                    color = PureWhiteSurface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, CleanCardBorder),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSubScreenOfSettings = currentScreen in listOf(
                            GoOrNoScreen.SECURITY,
                            GoOrNoScreen.SERVICES_ACTIVATION,
                            GoOrNoScreen.CONNECTION_STATUS,
                            GoOrNoScreen.DIAGNOSTICS_TESTS,
                            GoOrNoScreen.PRIVACY_BACKUP,
                            GoOrNoScreen.SUBSCRIPTION
                        )
                        val quickTabs = listOf(
                            Triple(GoOrNoScreen.HOME, Icons.Default.Dashboard, "Início"),
                            Triple(GoOrNoScreen.FINANCIAL_TARGETS, Icons.Default.Tune, "Ganhos"),
                            Triple(GoOrNoScreen.DRIVER_SETUP, Icons.Default.Layers, "Janela"),
                            Triple(GoOrNoScreen.HISTORY, Icons.Default.History, "Histórico"),
                            Triple(GoOrNoScreen.SETTINGS, Icons.Default.Settings, "Ajustes")
                        )
                        quickTabs.forEach { (screen, icon, label) ->
                            val selected = when (screen) {
                                GoOrNoScreen.HOME -> currentScreen == GoOrNoScreen.HOME
                                GoOrNoScreen.FINANCIAL_TARGETS -> currentScreen == GoOrNoScreen.FINANCIAL_TARGETS || currentScreen == GoOrNoScreen.VEHICLE_COSTS
                                GoOrNoScreen.DRIVER_SETUP -> currentScreen == GoOrNoScreen.DRIVER_SETUP
                                GoOrNoScreen.HISTORY -> currentScreen == GoOrNoScreen.HISTORY
                                GoOrNoScreen.SETTINGS -> currentScreen == GoOrNoScreen.SETTINGS || isSubScreenOfSettings
                                else -> false
                            }
                            Surface(
                                color = if (selected) PetrolHeader.copy(alpha = 0.10f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                border = if (selected) BorderStroke(1.dp, PetrolHeader.copy(alpha = 0.25f)) else null,
                                modifier = Modifier
                                    .clickable {
                                        currentScreen = when (screen) {
                                            GoOrNoScreen.SETTINGS -> GoOrNoScreen.SETTINGS
                                            GoOrNoScreen.FINANCIAL_TARGETS -> GoOrNoScreen.FINANCIAL_TARGETS
                                            else -> screen
                                        }
                                    }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (selected) PetrolHeader else MutedText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                        color = if (selected) PetrolHeader else MutedText,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
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
                if (currentScreen != GoOrNoScreen.HOME) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(
                            text = currentScreen.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = PetrolHeader
                        )
                        val screenSubtitle = when (currentScreen) {
                            GoOrNoScreen.FINANCIAL_TARGETS, GoOrNoScreen.VEHICLE_COSTS -> "Metas de lucro e custos operacionais do seu veículo"
                            GoOrNoScreen.DRIVER_SETUP -> "Escolha o formato e a transparência do aviso flutuante"
                            GoOrNoScreen.HISTORY -> "Auditoria de chamadas lidas e conferência de ganhos"
                            GoOrNoScreen.SETTINGS -> "Segurança, permissões e conexões com os aplicativos"
                            GoOrNoScreen.SECURITY -> "Cadastre ruas e bairros perigosos para alerta sonoro"
                            GoOrNoScreen.SERVICES_ACTIVATION -> "Permissões de acessibilidade, sobreposição e bateria"
                            GoOrNoScreen.CONNECTION_STATUS -> "Status de comunicação com os apps da Uber e 99"
                            GoOrNoScreen.DIAGNOSTICS_TESTS -> "Simulador de chamadas para testar o semáforo na tela"
                            GoOrNoScreen.PRIVACY_BACKUP -> "Exportar ou restaurar suas configurações salvas"
                            GoOrNoScreen.SUBSCRIPTION -> "Licença de uso, ativação e plano GoOrNo Pro"
                            else -> ""
                        }
                        if (screenSubtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screenSubtitle,
                                fontSize = 11.5.sp,
                                color = MutedText
                            )
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
                                                !config.copilotMonitoringEnabled -> "🔴 DESATIVADO"
                                                isCopilotActive -> "🟢 COPILOTO ATIVO"
                                                else -> "🟡 PENDENTE"
                                            },
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = when {
                                                !config.copilotMonitoringEnabled -> TrafficRed
                                                isCopilotActive -> TrafficGreen
                                                else -> TrafficYellow
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }

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
                                                        CopilotNotificationReceiver.cancelActiveNotification(context)
                                                        onStopAllBackgroundServices()
                                                        notifyUser("GoOrNo desativado! Notificação e leitura em segundo plano foram desligadas.")
                                                    } else {
                                                        CopilotNotificationReceiver.showActiveNotification(context)
                                                        RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                                                        notifyUser("GoOrNo ligado! Monitorando chamadas da Uber e 99 (sem nada flutuando em espera).")
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
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (config.copilotMonitoringEnabled && isPermsReady)
                                                Icons.Default.PowerSettingsNew
                                            else
                                                Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = when {
                                                !isPermsReady -> "Ativar"
                                                config.copilotMonitoringEnabled -> "Desativar"
                                                else -> "Ligar Copiloto"
                                            },
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = when {
                                        !config.copilotMonitoringEnabled ->
                                            "O GoOrNo está desligado e a barrinha do topo foi ocultada. Toque em 'Ligar Copiloto' quando for rodar."
                                        isCopilotActive ->
                                            "A janelinha abre SOZINHA ao tocar chamada ou abrir ofertas na Uber/99 e some após ${config.overlayAutoMinimizeSec}s."
                                        else ->
                                            "Ative os botões abaixo para o GoOrNo ler as ofertas da Uber e da 99 automaticamente."
                                    },
                                    fontSize = 12.sp,
                                    color = MutedText,
                                    modifier = Modifier.fillMaxWidth()
                                )



                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = CleanCardBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Checklist Rápido das 2 Permissões Essenciais
                                PermissionRowItem(
                                    number = "1",
                                    title = "Sobreposição de Tela",
                                    subtitle = "Exibe a barrinha ativa no topo e o painel sobre a Uber/99",
                                    granted = hasOverlayPermission,
                                    onAction = onRequestOverlayPermission
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PermissionRowItem(
                                    number = "2",
                                    title = "Acessibilidade GoOrNo",
                                    subtitle = "Lê R$, km, minutos e endereços automaticamente",
                                    granted = hasAccessibilityPermission,
                                    onAction = onRequestAccessibilityPermission
                                )

                                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                                val isBatteryUnrestricted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                                } else {
                                    true
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                PermissionRowItem(
                                    number = "3",
                                    title = "Sem Restrições de Bateria",
                                    subtitle = if (isBatteryUnrestricted)
                                        "Bateria sem restrições. O Android nunca fechará o GoOrNo sozinho."
                                    else
                                        "Toque para desativar as restrições de bateria do Android e evitar que ele pare sozinho.",
                                    granted = isBatteryUnrestricted,
                                    onAction = {
                                        try {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = android.net.Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }
                                        } catch (_: Exception) {
                                            try {
                                                val fallback = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(fallback)
                                            } catch (_: Exception) {
                                                notifyUser("Abra Configurações do Celular > Apps > GoOrNo > Bateria > Sem Restrições.")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // ATALHOS RÁPIDOS & FERRAMENTAS (Design Limpo e Otimizado)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ATALHOS RÁPIDOS",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )
                                Surface(
                                    color = TrafficGreen.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "• Praticidade no Volante",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TrafficGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Linha com 3 Cards Coloridos Centrais (Estilo Oficial dos Atalhos Rápidos)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickHubActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "🚦",
                                    title = "Semáforo",
                                    badgeText = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/km", config.targetGrossPerKm),
                                    badgeColor = Color(0xFF15803D),
                                    bgColor = Color(0xFFF0FDF4),
                                    borderColor = Color(0xFFBBF7D0),
                                    onClick = { currentScreen = GoOrNoScreen.FINANCIAL_TARGETS }
                                )
                                QuickHubActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "⛽",
                                    title = "Custo Carro",
                                    badgeText = String.format(Locale.forLanguageTag("pt-BR"), "%.1f km/L", config.fuelConsumptionKmPerLiter),
                                    badgeColor = Color(0xFF0369A1),
                                    bgColor = Color(0xFFF0F9FF),
                                    borderColor = Color(0xFFBAE6FD),
                                    onClick = { currentScreen = GoOrNoScreen.VEHICLE_COSTS }
                                )
                                QuickHubActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "🛡️",
                                    title = "Segurança",
                                    badgeText = if (config.riskAlertEnabled) "Ativo" else "Off",
                                    badgeColor = if (config.riskAlertEnabled) Color(0xFFBE123C) else Color(0xFF64748B),
                                    bgColor = if (config.riskAlertEnabled) Color(0xFFFFF1F2) else Color(0xFFF8FAFC),
                                    borderColor = if (config.riskAlertEnabled) Color(0xFFFECDD3) else Color(0xFFE2E8F0),
                                    onClick = { currentScreen = GoOrNoScreen.SECURITY }
                                )
                            }

                            // Ações Rápidas em Linha Cheia (Zero redundância com a barra de navegação)
                            QuickHubListActionRow(
                                icon = "🔘",
                                title = "Botão Flutuante",
                                subtitle = "Bolinha permanente na borda da tela",
                                badge = if (config.showFloatingShortcutBubble) "Visível" else "Oculto",
                                badgeColor = if (config.showFloatingShortcutBubble) TrafficGreen else MutedText,
                                onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP }
                            )

                            QuickHubListActionRow(
                                icon = "⚙️",
                                title = "Mais Ajustes",
                                subtitle = "Serviços, Conexão, Simulador, Backup e Pro",
                                badge = "Abrir",
                                badgeColor = PetrolHeader,
                                onClick = { currentScreen = GoOrNoScreen.SETTINGS }
                            )
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
                                        config = config,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) },
                                        onMarkAddressAsRisk = promptMarkAddressAsRisk,
                                        onMarkAddressAsFavorite = promptMarkAddressAsFavorite,
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
                // TELA: AJUSTES DO APLICATIVO (CENTRAL DE CONFIGURAÇÕES)
                // =====================================================
                GoOrNoScreen.SETTINGS -> {
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
                                    text = "SEGURANÇA & LEITURA",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )

                                QuickHubListActionRow(
                                    icon = "🛡️",
                                    title = "Áreas de Risco",
                                    subtitle = "Bairros e ruas perigosas com aviso sonoro e bloqueio",
                                    badge = if (config.riskAlertEnabled) "Ativo" else "Off",
                                    badgeColor = if (config.riskAlertEnabled) TrafficRed else MutedText,
                                    onClick = {
                                        securitySelectedTab = 0
                                        currentScreen = GoOrNoScreen.SECURITY
                                    }
                                )

                                QuickHubListActionRow(
                                    icon = "⭐",
                                    title = "Regiões Favoritas",
                                    subtitle = "Bairros e destinos preferidos com destaque prioritário",
                                    badge = if (config.favoriteZonesEnabled) "Ativo" else "Off",
                                    badgeColor = if (config.favoriteZonesEnabled) TrafficGreen else MutedText,
                                    onClick = {
                                        securitySelectedTab = 1
                                        currentScreen = GoOrNoScreen.SECURITY
                                    }
                                )

                                val isPermsReady = hasOverlayPermission && hasAccessibilityPermission
                                QuickHubListActionRow(
                                    icon = "⚡",
                                    title = "Permissões",
                                    subtitle = "Sobreposição, Acessibilidade e Bateria",
                                    badge = if (isPermsReady) "OK" else "Ativar",
                                    badgeColor = if (isPermsReady) TrafficGreen else TrafficYellow,
                                    onClick = { currentScreen = GoOrNoScreen.SERVICES_ACTIVATION }
                                )

                                QuickHubListActionRow(
                                    icon = "🚗",
                                    title = "Conexão dos Apps",
                                    subtitle = "Detecção de aplicativos e status de leitura",
                                    badge = if (accessibilityConnected) "Conectado" else "Aguardando",
                                    badgeColor = if (accessibilityConnected) TrafficGreen else MutedText,
                                    onClick = { currentScreen = GoOrNoScreen.CONNECTION_STATUS }
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = CleanCardBorder)
                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "RECURSOS & CONTA",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )

                                QuickHubListActionRow(
                                    icon = "⭐",
                                    title = "Plano Pro",
                                    subtitle = "Chave de ativação, renovação e PIX",
                                    badge = "PRO",
                                    badgeColor = PetrolHeader,
                                    onClick = { currentScreen = GoOrNoScreen.SUBSCRIPTION }
                                )

                                QuickHubListActionRow(
                                    icon = "🧪",
                                    title = "Simulador",
                                    subtitle = "Simular chamadas na tela para testar o semáforo",
                                    badge = "Testar",
                                    badgeColor = Color(0xFF0284C7),
                                    onClick = { currentScreen = GoOrNoScreen.DIAGNOSTICS_TESTS }
                                )

                                QuickHubListActionRow(
                                    icon = "💾",
                                    title = "Backup & Nuvem",
                                    subtitle = "Exportar/restaurar dados e configurações",
                                    badge = "Backup",
                                    badgeColor = PetrolHeader,
                                    onClick = { currentScreen = GoOrNoScreen.PRIVACY_BACKUP }
                                )
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 2: FORMATO DA JANELA FLUTUANTE & MOTORISTA
                // =====================================================
                // =====================================================
                // TELA 2: ESTILO DO CARTÃO & ATALHOS FLUTUANTES
                // =====================================================
                GoOrNoScreen.DRIVER_SETUP -> {
                    item {
                        var driverName by remember(config.driverName) { mutableStateOf(config.driverName) }
                        var vehicleModel by remember(config.vehicleModel) { mutableStateOf(config.vehicleModel) }
                        var selectedStyle by remember(config.overlayDisplayStyle) {
                            mutableStateOf(OverlayDisplayStyle.fromName(config.overlayDisplayStyle))
                        }
                        var overlayTheme by remember(config.overlayTheme) {
                            mutableStateOf(config.overlayTheme)
                        }
                        var bubblePosition by remember(config.overlayBubblePosition) {
                            mutableStateOf(config.overlayBubblePosition)
                        }
                        var horizontalPosition by remember(config.overlayHorizontalPosition) {
                            mutableStateOf(config.overlayHorizontalPosition)
                        }
                        var showShortcutBubble by remember(config.showFloatingShortcutBubble) {
                            mutableStateOf(config.showFloatingShortcutBubble)
                        }
                        var showProfitOnOverlay by remember(config.showProfitOnOverlay) {
                            mutableStateOf(config.showProfitOnOverlay)
                        }
                        var showCostOnOverlay by remember(config.showCostOnOverlay) {
                            mutableStateOf(config.showCostOnOverlay)
                        }
                        var opacity by remember(config.overlayOpacityPercent) {
                            mutableFloatStateOf(config.overlayOpacityPercent.toFloat())
                        }
                        var autoMinSec by remember(config.overlayAutoMinimizeSec) {
                            mutableStateOf(config.overlayAutoMinimizeSec.toString())
                        }
                        var showLivePreviewModal by remember { mutableStateOf(false) }
                        var previewVerdictState by remember { mutableStateOf("GREEN") }
                        var savedFlag by remember { mutableStateOf(false) }

                        // Modal Interativo de Pré-visualização do Cartão (VISUALIZAR)
                        if (showLivePreviewModal) {
                            AlertDialog(
                                onDismissRequest = { showLivePreviewModal = false },
                                confirmButton = {
                                    Button(
                                        onClick = { showLivePreviewModal = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader)
                                    ) {
                                        Text("Fechar Prévia", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Prévia da Janela", fontWeight = FontWeight.Black, fontSize = 16.sp, color = PetrolHeader)
                                        IconButton(onClick = { showLivePreviewModal = false }) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = MutedText)
                                        }
                                    }
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Selecione o veredito para ver como o card e o balão aparecem:",
                                            fontSize = 12.sp,
                                            color = MutedText
                                        )

                                        // Chips de seleção de veredito: Aceitar / Analisar / Recusar
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                Triple("GREEN", "✓ Aceitar", TrafficGreen),
                                                Triple("YELLOW", "● Analisar", TrafficYellow),
                                                Triple("RED", "✕ Recusar", TrafficRed)
                                            ).forEach { (key, label, color) ->
                                                val isSel = previewVerdictState == key
                                                Surface(
                                                    color = if (isSel) color else SoftSurfaceVariant,
                                                    shape = RoundedCornerShape(16.dp),
                                                    border = BorderStroke(1.dp, if (isSel) color else CleanCardBorder),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { previewVerdictState = key }
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = if (isSel) Color.White else PetrolDarkText,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Card Renderizado Fielmente de Acordo com o Estilo Selecionado
                                        val vColor = when (previewVerdictState) {
                                            "GREEN" -> Color(0xFF00C853)
                                            "YELLOW" -> Color(0xFFFACC15)
                                            else -> Color(0xFFEF4444)
                                        }
                                        val vText = when (previewVerdictState) {
                                            "GREEN" -> "✓  ACEITAR"
                                            "YELLOW" -> "●  AVALIAR"
                                            else -> "✕  RECUSAR"
                                        }
                                        val vTextColor = when (previewVerdictState) {
                                            "GREEN" -> Color(0xFF052E16)
                                            "YELLOW" -> Color(0xFF422006)
                                            else -> Color.White
                                        }
                                        val isAbove = bubblePosition != "BELOW"

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            when (selectedStyle) {
                                                OverlayDisplayStyle.BANNER_PRO_STREETVIEW -> {
                                                    // FORMATO: CARD IMPACTO PRO - CORRIDA TOCOU (DINÂMICO CLARO/ESCURO)
                                                    val isDarkBanner = overlayTheme != "LIGHT"
                                                    val bannerCardBg = if (isDarkBanner) Color(0xFF0D0D0E) else Color.White
                                                    val bannerPrimaryText = if (isDarkBanner) Color.White else Color(0xFF111827)
                                                    val bannerSecondaryText = if (isDarkBanner) Color(0xFF9CA3AF) else Color(0xFF4B5563)
                                                    val bannerMetricsText = if (isDarkBanner) Color(0xFFD1D5DB) else Color(0xFF374151)
                                                    val bannerTimerBg = if (isDarkBanner) Color(0xFF1E1E22) else Color(0xFFE5E7EB)

                                                    Surface(
                                                        color = bannerCardBg,
                                                        shape = RoundedCornerShape(14.dp),
                                                        border = BorderStroke(2.dp, vColor),
                                                        shadowElevation = 8.dp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column {
                                                            Column(modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 10.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "8 km · 15 min",
                                                                        color = bannerSecondaryText,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Surface(
                                                                            color = bannerTimerBg,
                                                                            shape = RoundedCornerShape(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "15s",
                                                                                color = bannerSecondaryText,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            )
                                                                        }
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text(
                                                                            text = "✕",
                                                                            color = bannerSecondaryText,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.height(4.dp))

                                                                Text(
                                                                    text = "R$ 18,40",
                                                                    color = bannerPrimaryText,
                                                                    fontSize = 34.sp,
                                                                    fontWeight = FontWeight.Black
                                                                )

                                                                Spacer(modifier = Modifier.height(2.dp))

                                                                Text(
                                                                    text = "R$ 2,30/km · R$ 43,00/h",
                                                                    color = bannerMetricsText,
                                                                    fontSize = 13.5.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }

                                                            Surface(
                                                                color = vColor,
                                                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                                                                modifier = Modifier.fillMaxWidth().height(42.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Text(
                                                                        text = when (previewVerdictState) {
                                                                            "GREEN" -> "✓ COMPENSA"
                                                                            "YELLOW" -> "⚡ AVALIAR"
                                                                            else -> "✕ NÃO COMPENSA"
                                                                        },
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Black,
                                                                        fontSize = 14.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                OverlayDisplayStyle.PIRULITO_SIDEBAR -> {
                                                    // FORMATO: CARD 3 PILARES (R$/KM, R$/HORA E NOTA)
                                                    val isDarkPreview = overlayTheme != "LIGHT"
                                                    val cardBg = if (isDarkPreview) Color(0xFF121316) else Color.White
                                                    val primaryText = if (isDarkPreview) Color.White else Color(0xFF111827)
                                                    val secondaryText = if (isDarkPreview) Color(0xFF9CA3AF) else Color(0xFF6B7280)

                                                    Surface(
                                                        color = cardBg,
                                                        shape = RoundedCornerShape(18.dp),
                                                        border = BorderStroke(2.5.dp, vColor),
                                                        shadowElevation = 8.dp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp, 10.dp, 14.dp, 10.dp)) {
                                                            // Linha Superior: Resumo + Timer + Fechar
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "R$ 12,70 · 11 min · 5,6 km",
                                                                    color = primaryText,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Text("15s", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("✕", color = secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                            }

                                                            Spacer(modifier = Modifier.height(10.dp))

                                                            // 3 Colunas: R$/Km | R$/Hora | Nota
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                // Coluna 1: R$/Km
                                                                Column(
                                                                    modifier = Modifier.weight(1f),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text("R$/Km", color = secondaryText, fontSize = 11.sp)
                                                                    Text("2,26", color = primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                                                }

                                                                // Barra 1
                                                                Box(modifier = Modifier.size(3.dp, 36.dp).background(vColor, RoundedCornerShape(2.dp)))

                                                                // Coluna 2: R$/Hora
                                                                Column(
                                                                    modifier = Modifier.weight(1f),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text("R$/Hora", color = secondaryText, fontSize = 11.sp)
                                                                    Text("69", color = primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                                                }

                                                                // Barra 2
                                                                Box(modifier = Modifier.size(3.dp, 36.dp).background(vColor, RoundedCornerShape(2.dp)))

                                                                // Coluna 3: Nota
                                                                Column(
                                                                    modifier = Modifier.weight(1f),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text("Nota", color = secondaryText, fontSize = 11.sp)
                                                                    Text("4,95", color = vColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                OverlayDisplayStyle.CLASSIC_LIGHT -> {
                                                    // FORMATO 2: CLÁSSICO TRADICIONAL (DINÂMICO CLARO/ESCURO)
                                                    val isDarkClassic = overlayTheme == "DARK"
                                                    val classicCardBg = if (isDarkClassic) Color(0xFF111F1A) else Color.White
                                                    val classicDragTitle = if (isDarkClassic) Color(0xFF94A3B8) else Color(0xFF334155)
                                                    val classicTimerColor = if (isDarkClassic) Color(0xFFCBD5E1) else Color(0xFF64748B)
                                                    val classicInnerPriceBg = if (isDarkClassic) Color(0xFF0D1714) else Color(0xFFF8FAF9)
                                                    val classicPriceColor = if (isDarkClassic) Color(0xFF4ADE80) else Color(0xFF073B32)
                                                    val classicDistColor = if (isDarkClassic) Color(0xFFCBD5E1) else Color(0xFF334155)
                                                    val classicMetricPillBg = if (isDarkClassic) Color(0xFF0D1F19) else Color(0xFFDCFCE7)
                                                    val classicMetricPillText = if (isDarkClassic) Color(0xFF4ADE80) else Color(0xFF166534)

                                                    Surface(
                                                        color = classicCardBg,
                                                        shape = RoundedCornerShape(18.dp),
                                                        border = BorderStroke(1.5.dp, vColor),
                                                        shadowElevation = 8.dp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp, 10.dp, 14.dp, 10.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "⠿ Uber (arraste)",
                                                                    color = classicDragTitle,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = "7s",
                                                                        color = classicTimerColor,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(end = 6.dp)
                                                                    )
                                                                    Surface(
                                                                        color = Color(0xFFDC2626),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "✕ Fechar",
                                                                            color = Color.White,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(8.dp))

                                                            Surface(
                                                                color = classicInnerPriceBg,
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(1.dp, vColor.copy(alpha = 0.5f)),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(modifier = Modifier.padding(10.dp)) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            text = "R$ 14,01",
                                                                            color = classicPriceColor,
                                                                            fontSize = 18.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            maxLines = 1,
                                                                            softWrap = false
                                                                        )
                                                                        Surface(
                                                                            color = vColor,
                                                                            shape = RoundedCornerShape(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = when (previewVerdictState) {
                                                                                    "GREEN" -> "ACEITAR"
                                                                                    "YELLOW" -> "AVALIAR"
                                                                                    else -> "RECUSAR"
                                                                                },
                                                                                color = vTextColor,
                                                                                fontSize = 10.sp,
                                                                                fontWeight = FontWeight.Black,
                                                                                maxLines = 1,
                                                                                softWrap = false,
                                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                                            )
                                                                        }
                                                                        Text(
                                                                            text = "6,3 km · 17 min",
                                                                            color = classicDistColor,
                                                                            fontSize = 11.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1,
                                                                            softWrap = false
                                                                        )
                                                                    }

                                                                    Spacer(modifier = Modifier.height(8.dp))

                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        Surface(
                                                                            color = classicMetricPillBg,
                                                                            shape = RoundedCornerShape(8.dp),
                                                                            border = BorderStroke(1.dp, vColor),
                                                                            modifier = Modifier.weight(1f)
                                                                        ) {
                                                                            Text(
                                                                                text = "R$ 2,22/km",
                                                                                color = classicMetricPillText,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                textAlign = TextAlign.Center,
                                                                                maxLines = 1,
                                                                                softWrap = false,
                                                                                modifier = Modifier.padding(vertical = 5.dp)
                                                                            )
                                                                        }
                                                                        Surface(
                                                                            color = classicMetricPillBg,
                                                                            shape = RoundedCornerShape(8.dp),
                                                                            border = BorderStroke(1.dp, vColor),
                                                                            modifier = Modifier.weight(1f)
                                                                        ) {
                                                                            Text(
                                                                                text = "R$ 49/h",
                                                                                color = classicMetricPillText,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                textAlign = TextAlign.Center,
                                                                                maxLines = 1,
                                                                                softWrap = false,
                                                                                modifier = Modifier.padding(vertical = 5.dp)
                                                                            )
                                                                        }
                                                                        Surface(
                                                                            color = Color(0xFF16A34A),
                                                                            shape = RoundedCornerShape(8.dp),
                                                                            modifier = Modifier.weight(1f)
                                                                        ) {
                                                                            Text(
                                                                                text = "★ 4.96",
                                                                                color = Color.White,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                textAlign = TextAlign.Center,
                                                                                maxLines = 1,
                                                                                softWrap = false,
                                                                                modifier = Modifier.padding(vertical = 5.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                OverlayDisplayStyle.MINI_BALL -> {
                                                    // FORMATO 3: BOLINHA SEMÁFORO ULTRA DISCRETO (DINÂMICO CLARO/ESCURO)
                                                    val isDarkMini = overlayTheme != "LIGHT"
                                                    val miniCardBg = if (isDarkMini) Color(0xFF0D1714) else Color.White
                                                    val miniPrimaryText = if (isDarkMini) Color.White else Color(0xFF111827)
                                                    val miniSecondaryText = if (isDarkMini) Color(0xFF94A3B8) else Color(0xFF6B7280)

                                                    Surface(
                                                        color = miniCardBg,
                                                        shape = RoundedCornerShape(18.dp),
                                                        border = BorderStroke(1.5.dp, vColor),
                                                        shadowElevation = 8.dp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(12.dp, 8.dp, 10.dp, 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "⠿",
                                                                color = miniSecondaryText,
                                                                fontSize = 13.sp,
                                                                modifier = Modifier.padding(end = 4.dp)
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(16.dp)
                                                                    .background(vColor, CircleShape)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "Uber · 6,3 km · 17 min",
                                                                    color = miniPrimaryText,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1
                                                                )
                                                                Text(
                                                                    text = "R$ 2,22 | 49,00 · ★ 4.96",
                                                                    color = vColor,
                                                                    fontSize = 13.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "7s",
                                                                color = miniSecondaryText,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(end = 6.dp)
                                                            )
                                                            Surface(
                                                                color = Color(0xFFDC2626),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "✕",
                                                                    color = Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Header com Botão VISUALIZAR (conforme solicitado e padrão moderno)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ESTILO DO CARTÃO",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = PetrolHeader
                                        )
                                        Text(
                                            text = "Personalize o formato, balão de fala e atalhos na tela",
                                            fontSize = 11.5.sp,
                                            color = MutedText
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showLivePreviewModal = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = PetrolHeader,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = LimeBright)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("VISUALIZAR", fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                                    }
                                }

                                Surface(
                                    color = TrafficGreenSoft.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, TrafficGreen)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "🖐️ Janela Livremente Arrastável na Tela!",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = PetrolHeader
                                        )
                                        Text(
                                            text = "Quando a janelinha abrir (ícone ⠿), segure e arraste com o dedo para onde preferir na tela. A posição escolhida fica salva automaticamente para não tampar botões da Uber ou 99.",
                                            fontSize = 11.5.sp,
                                            color = PetrolDarkText
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                app.overlayPanel.resetSavedPosition()
                                                notifyUser("Posição da janela redefinida para o padrão!")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Voltar Janela para Posição Inicial Padrão",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = PetrolHeader
                                            )
                                        }
                                    }
                                }

                                // Seletor de Formato do Cartão
                                Text(
                                    text = "Formato do Cartão",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = PetrolHeader
                                )

                                OverlayDisplayStyle.entries.forEach { style ->
                                    val isChosen = selectedStyle == style
                                    Surface(
                                        color = if (isChosen) TrafficGreenSoft.copy(alpha = 0.65f) else SoftSurfaceVariant,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(if (isChosen) 2.dp else 1.dp, if (isChosen) TrafficGreen else CleanCardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedStyle = style
                                                savedFlag = false
                                                val updated = config.copy(overlayDisplayStyle = style.name)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    app.overlayPanel.applyConfig(updated)
                                                    notifyUser("Formato '${style.title}' ativado!")
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isChosen,
                                                onClick = {
                                                    selectedStyle = style
                                                    savedFlag = false
                                                    val updated = config.copy(overlayDisplayStyle = style.name)
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.applyConfig(updated)
                                                        notifyUser("Formato '${style.title}' ativado!")
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = style.title,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.5.sp,
                                                    color = PetrolDarkText
                                                )
                                                Text(
                                                    text = style.subtitle,
                                                    fontSize = 11.sp,
                                                    color = MutedText
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Tema do Cartão (Fundo Claro ou Escuro)
                                Text(
                                    text = "Tema do Cartão (Fundo)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Escolha se o fundo do cartão deve ser claro (branco) ou escuro (preto):",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Pair("LIGHT", "☀️ Claro (Branco)"),
                                        Pair("DARK", "🌙 Escuro (Preto)")
                                    ).forEach { (themeKey, label) ->
                                        val isSel = overlayTheme == themeKey
                                        Surface(
                                            color = if (isSel) PetrolHeader else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, if (isSel) PetrolHeader else CleanCardBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    overlayTheme = themeKey
                                                    savedFlag = false
                                                    val updated = config.copy(
                                                        overlayTheme = themeKey,
                                                        overlayHorizontalPosition = "CENTER"
                                                    )
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.applyConfig(updated)
                                                        notifyUser("Tema alterado: $label")
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isSel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSel) LimeBright else MutedText,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = label,
                                                    color = if (isSel) Color.White else PetrolDarkText,
                                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }



                                HorizontalDivider(color = CleanCardBorder)

                                // Botão Flutuante de Atalho na Tela (com opção de ocultar se incomodar)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Botão Flutuante de Atalho na Tela",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = "Bolinha permanente na borda da tela. Toque nela sobre a Uber/99 para abrir Semáforo, Metas e Ferramentas rapidamente.",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                    Switch(
                                        checked = showShortcutBubble,
                                        onCheckedChange = { checked ->
                                            showShortcutBubble = checked
                                            savedFlag = false
                                            val updated = config.copy(showFloatingShortcutBubble = checked)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                                notifyUser(if (checked) "Botão flutuante ativado na tela!" else "Botão flutuante ocultado!")
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Informações Extras na Janela e na Notificação (Opcional)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Por padrão, Lucro e Custo ficam ocultos para deixar a leitura limpa. Ative abaixo se quiser exibi-los:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mostrar Lucro Líquido (Janela + Notificação)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = "Exibe quanto sobra limpo descontando o gasto do carro",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                    Switch(
                                        checked = showProfitOnOverlay,
                                        onCheckedChange = { checked ->
                                            showProfitOnOverlay = checked
                                            savedFlag = false
                                            val updated = config.copy(showProfitOnOverlay = checked)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                                if (updated.copilotMonitoringEnabled) {
                                                    CopilotNotificationReceiver.showActiveNotification(
                                                        context = context,
                                                        showProfit = checked,
                                                        showCost = showCostOnOverlay
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mostrar Custo do Carro (Janela + Notificação)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = "Exibe quanto o veículo gasta de combustível/manutenção na corrida",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                    Switch(
                                        checked = showCostOnOverlay,
                                        onCheckedChange = { checked ->
                                            showCostOnOverlay = checked
                                            savedFlag = false
                                            val updated = config.copy(showCostOnOverlay = checked)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                                if (updated.copilotMonitoringEnabled) {
                                                    CopilotNotificationReceiver.showActiveNotification(
                                                        context = context,
                                                        showProfit = showProfitOnOverlay,
                                                        showCost = checked
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                Text(
                                    text = "Transparência da Janelinha Flutuante: ${opacity.toInt()}%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
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

                                Text(
                                    text = "Tempo da janelinha aberta antes de sumir:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PetrolDarkText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(3, 4, 5, 6, 7).forEach { sec ->
                                        val selected = autoMinSec == sec.toString()
                                        FilledTonalButton(
                                            onClick = {
                                                autoMinSec = sec.toString()
                                                savedFlag = false
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (selected) PetrolHeader else SoftSurfaceVariant,
                                                contentColor = if (selected) Color.White else PetrolDarkText
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Text("${sec}s", fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

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

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            driverName = driverName.ifBlank { "Motorista" },
                                            vehicleModel = vehicleModel.ifBlank { "Veículo" },
                                            overlayDisplayStyle = selectedStyle.name,
                                            overlayTheme = overlayTheme,
                                            overlayBubblePosition = bubblePosition,
                                            overlayHorizontalPosition = "CENTER",
                                            showFloatingShortcutBubble = showShortcutBubble,
                                            showProfitOnOverlay = showProfitOnOverlay,
                                            showCostOnOverlay = showCostOnOverlay,
                                            overlayOpacityPercent = opacity.toInt().coerceIn(25, 100),
                                            overlayAutoMinimizeSec = (autoMinSec.toIntOrNull() ?: 5).coerceIn(3, 7)
                                        )
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            app.overlayPanel.applyConfig(updated)
                                            savedFlag = true
                                            notifyUser("Formato da janelinha e dados salvos com sucesso!")
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
                                        text = if (savedFlag) "✓ Configuração Salva com Sucesso!" else "Salvar Formato e Configuração",
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
                        EarningsPillTabSelector(
                            currentScreen = currentScreen,
                            onSelectScreen = { currentScreen = it }
                        )
                    }
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
                                    color = TrafficGreenSoft.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, TrafficGreen)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "💡 Entenda: O que é CUSTO e o que é LUCRO?",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = PetrolHeader
                                        )
                                        Text(
                                            text = "• CUSTO: É quanto o seu carro gasta do seu bolso (Combustível + Pneu/Óleo/Manutenção) para rodar os KMs da corrida.\n" +
                                                "• LUCRO: É o que SOBRA LIMPO no seu bolso depois de descontar esse gasto do carro (Valor da Corrida − Custo do Carro).",
                                            fontSize = 11.5.sp,
                                            color = PetrolDarkText
                                        )
                                    }
                                }

                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Custo Total por KM:",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f / km", config.totalVariableCostPerKm),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = PetrolHeader
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = PureWhiteSurface,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, CleanCardBorder),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("COMBUSTÍVEL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText)
                                                    Text(
                                                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/km", config.fuelCostPerKm),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = PetrolDarkText,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = PureWhiteSurface,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, CleanCardBorder),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("MANUTENÇÃO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText)
                                                    Text(
                                                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f/km", config.maintenanceCostPerKm),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = PetrolDarkText,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                        EarningsPillTabSelector(
                            currentScreen = currentScreen,
                            onSelectScreen = { currentScreen = it }
                        )
                    }
                    item {
                        VisualDualTargetSliderCard(
                            title = "Ganho por quilômetro",
                            description = "Define quando o R$/km da corrida é ruim, OK ou bom.",
                            unitSuffix = "/km",
                            minValue = config.minGrossPerKm,
                            goodValue = config.targetGrossPerKm,
                            rangeMin = 1.0,
                            rangeMax = 8.0,
                            step = 0.10,
                            onMinChange = { newVal ->
                                val updated = config.copy(
                                    minGrossPerKm = newVal,
                                    yellowToleranceRatio = if (config.targetGrossPerKm > 0.0) (newVal / config.targetGrossPerKm).coerceIn(0.5, 0.95) else config.yellowToleranceRatio
                                )
                                scope.launch {
                                    app.preferencesRepository.saveConfig(updated)
                                    app.overlayPanel.applyConfig(updated)
                                }
                            },
                            onGoodChange = { newVal ->
                                val updated = config.copy(
                                    targetGrossPerKm = newVal,
                                    yellowToleranceRatio = if (newVal > 0.0) (config.minGrossPerKm / newVal).coerceIn(0.5, 0.95) else config.yellowToleranceRatio
                                )
                                scope.launch {
                                    app.preferencesRepository.saveConfig(updated)
                                    app.overlayPanel.applyConfig(updated)
                                }
                            }
                        )
                    }
                    item {
                        VisualDualTargetSliderCard(
                            title = "Ganho por hora",
                            description = "Define quando o R$/hora da corrida é ruim, OK ou bom.",
                            unitSuffix = "/h",
                            minValue = config.minGrossPerHour,
                            goodValue = config.targetGrossPerHour,
                            rangeMin = 20.0,
                            rangeMax = 150.0,
                            step = 5.0,
                            onMinChange = { newVal ->
                                val updated = config.copy(
                                    minGrossPerHour = newVal,
                                    yellowToleranceRatio = if (config.targetGrossPerHour > 0.0) (newVal / config.targetGrossPerHour).coerceIn(0.5, 0.95) else config.yellowToleranceRatio
                                )
                                scope.launch {
                                    app.preferencesRepository.saveConfig(updated)
                                    app.overlayPanel.applyConfig(updated)
                                }
                            },
                            onGoodChange = { newVal ->
                                val updated = config.copy(
                                    targetGrossPerHour = newVal,
                                    yellowToleranceRatio = if (newVal > 0.0) (config.minGrossPerHour / newVal).coerceIn(0.5, 0.95) else config.yellowToleranceRatio
                                )
                                scope.launch {
                                    app.preferencesRepository.saveConfig(updated)
                                    app.overlayPanel.applyConfig(updated)
                                }
                            }
                        )
                    }
                    item {
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
                                    text = "Critérios Obrigatórios do Semáforo",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Escolha quais critérios a oferta deve cumprir obrigatoriamente para receber sinal VERDE no card flutuante:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newVal = !reqGrossKm
                                            reqGrossKm = newVal
                                            val updated = config.copy(requireGrossPerKm = newVal)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                ) {
                                    Checkbox(
                                        checked = reqGrossKm,
                                        onCheckedChange = {
                                            reqGrossKm = it
                                            val updated = config.copy(requireGrossPerKm = it)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exigir Ganho Bruto/km na classificação", fontSize = 12.5.sp, color = PetrolDarkText, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newVal = !reqGrossHr
                                            reqGrossHr = newVal
                                            val updated = config.copy(requireGrossPerHour = newVal)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                ) {
                                    Checkbox(
                                        checked = reqGrossHr,
                                        onCheckedChange = {
                                            reqGrossHr = it
                                            val updated = config.copy(requireGrossPerHour = it)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exigir Ganho Bruto/hora na classificação", fontSize = 12.5.sp, color = PetrolDarkText, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newVal = !reqNetKm
                                            reqNetKm = newVal
                                            val updated = config.copy(requireNetPerKm = newVal)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                ) {
                                    Checkbox(
                                        checked = reqNetKm,
                                        onCheckedChange = {
                                            reqNetKm = it
                                            val updated = config.copy(requireNetPerKm = it)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exigir Lucro Líquido/km na classificação", fontSize = 12.5.sp, color = PetrolDarkText, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newVal = !reqNetHr
                                            reqNetHr = newVal
                                            val updated = config.copy(requireNetPerHour = newVal)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                ) {
                                    Checkbox(
                                        checked = reqNetHr,
                                        onCheckedChange = {
                                            reqNetHr = it
                                            val updated = config.copy(requireNetPerHour = it)
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(updated)
                                                app.overlayPanel.applyConfig(updated)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exigir Lucro Líquido/hora na classificação", fontSize = 12.5.sp, color = PetrolDarkText, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // =========================================================
                        // CARD DE AVALIAÇÃO DO PASSAGEIRO (VERDE / AMARELA / VERMELHA)
                        // =========================================================
                        var goodRatingText by remember(config.targetGoodPassengerRating) {
                            mutableStateOf(String.format(Locale.US, "%.2f", config.targetGoodPassengerRating))
                        }
                        var minRatingText by remember(config.minAcceptablePassengerRating) {
                            mutableStateOf(String.format(Locale.US, "%.2f", config.minAcceptablePassengerRating))
                        }
                        var savedRatingFlag by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.5.dp, PetrolHeader.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = PetrolHeader,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "★ NOTA",
                                            color = LimeBright,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Avaliação do Passageiro (Critério e Cores)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = PetrolHeader
                                    )
                                }

                                Text(
                                    text = "Configure qual nota de passageiro você considera BOA (Verde), MÉDIA (Amarela) ou RUIM (Vermelha), e se ela deve alterar o Semáforo de aceitar ou não:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                // Legenda visual harmônica e alinhada das 3 cores da nota do passageiro (Altura igual e sem quebra feia)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Card Verde
                                    Surface(
                                        color = TrafficGreen,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Text(
                                                    text = "BOA",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = ">= ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", config.targetGoodPassengerRating)}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // Card Amarelo
                                    Surface(
                                        color = TrafficYellow,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Text(
                                                    text = "MÉDIA",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", config.minAcceptablePassengerRating)} – ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", (config.targetGoodPassengerRating - 0.01).coerceAtLeast(config.minAcceptablePassengerRating))}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // Card Vermelho
                                    Surface(
                                        color = TrafficRed,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Text(
                                                    text = "RUIM",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "< ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", config.minAcceptablePassengerRating)}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // Controles de Ajuste das Notas (diretamente abaixo das cores)
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Ajustar Valores das Notas (+ ou -):",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = PetrolHeader,
                                            maxLines = 1
                                        )

                                        // Linha Nota Boa
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(TrafficGreen))
                                                Text("Nota Boa:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PetrolDarkText, maxLines = 1, softWrap = false)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Surface(
                                                    onClick = {
                                                        val updatedGood = ((config.targetGoodPassengerRating - 0.05) * 100).roundToInt() / 100.0
                                                        val valid = updatedGood.coerceIn(config.minAcceptablePassengerRating + 0.05, 5.0)
                                                        goodRatingText = String.format(Locale.US, "%.2f", valid)
                                                        scope.launch {
                                                             app.preferencesRepository.saveConfig(config.copy(targetGoodPassengerRating = valid))
                                                            notifyUser("Nota Boa ajustada para ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valid)}!")
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFF3E8FF),
                                                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                                    modifier = Modifier.width(42.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text("-0,05", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF581C87), maxLines = 1, softWrap = false)
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, CleanCardBorder),
                                                    color = PureWhiteSurface,
                                                    modifier = Modifier.width(50.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        BasicTextField(
                                                            value = goodRatingText,
                                                            onValueChange = { goodRatingText = it.replace(",", "."); savedRatingFlag = false },
                                                            textStyle = LocalTextStyle.current.copy(
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = PetrolDarkText,
                                                                textAlign = TextAlign.Center
                                                            ),
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    onClick = {
                                                        val updatedGood = ((config.targetGoodPassengerRating + 0.05) * 100).roundToInt() / 100.0
                                                        val valid = updatedGood.coerceIn(4.0, 5.0)
                                                        goodRatingText = String.format(Locale.US, "%.2f", valid)
                                                        scope.launch {
                                                            app.preferencesRepository.saveConfig(config.copy(targetGoodPassengerRating = valid))
                                                            notifyUser("Nota Boa ajustada para ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valid)}!")
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFF3E8FF),
                                                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                                    modifier = Modifier.width(42.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text("+0,05", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF581C87), maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = CleanCardBorder)

                                        // Linha Nota Mínima
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(TrafficYellow))
                                                Text("Nota Mínima:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PetrolDarkText, maxLines = 1, softWrap = false)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Surface(
                                                    onClick = {
                                                        val updatedMin = ((config.minAcceptablePassengerRating - 0.05) * 100).roundToInt() / 100.0
                                                        val valid = updatedMin.coerceIn(3.0, config.targetGoodPassengerRating - 0.05)
                                                        minRatingText = String.format(Locale.US, "%.2f", valid)
                                                        scope.launch {
                                                            app.preferencesRepository.saveConfig(config.copy(minAcceptablePassengerRating = valid))
                                                            notifyUser("Nota Mínima ajustada para ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valid)}!")
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFF3E8FF),
                                                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                                    modifier = Modifier.width(42.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text("-0,05", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF581C87), maxLines = 1, softWrap = false)
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, CleanCardBorder),
                                                    color = PureWhiteSurface,
                                                    modifier = Modifier.width(50.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        BasicTextField(
                                                            value = minRatingText,
                                                            onValueChange = { minRatingText = it.replace(",", "."); savedRatingFlag = false },
                                                            textStyle = LocalTextStyle.current.copy(
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = PetrolDarkText,
                                                                textAlign = TextAlign.Center
                                                            ),
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    onClick = {
                                                        val updatedMin = ((config.minAcceptablePassengerRating + 0.05) * 100).roundToInt() / 100.0
                                                        val valid = updatedMin.coerceIn(3.0, config.targetGoodPassengerRating - 0.05)
                                                        minRatingText = String.format(Locale.US, "%.2f", valid)
                                                        scope.launch {
                                                            app.preferencesRepository.saveConfig(config.copy(minAcceptablePassengerRating = valid))
                                                            notifyUser("Nota Mínima ajustada para ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valid)}!")
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFF3E8FF),
                                                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                                    modifier = Modifier.width(42.dp).height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text("+0,05", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF581C87), maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Usar Nota do Passageiro como Critério de Aceitar",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = "Nota Média muda Verde p/ Amarelo (AVALIAR); Nota Ruim reprova a corrida",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.requirePassengerRating,
                                            onCheckedChange = { req ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(requirePassengerRating = req))
                                                    notifyUser(if (req) "Nota do passageiro ativada como critério!" else "Nota do passageiro exibida apenas como informação.")
                                                }
                                            }
                                        )
                                    }
                                }

                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Forçar VERMELHO (Recusar) se Nota for Ruim",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = TrafficRed
                                            )
                                            Text(
                                                text = "Se a nota for menor que o mínimo amarelo, bloqueia no semáforo vermelho",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.forceRedOnBadPassengerRating,
                                            onCheckedChange = { forceRed ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(forceRedOnBadPassengerRating = forceRed))
                                                    notifyUser(if (forceRed) "Nota Ruim forçará Semáforo VERMELHO!" else "Nota Ruim mudará para AVALIAR.")
                                                }
                                            }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val goodVal = (goodRatingText.toDoubleOrNull() ?: 4.85).coerceIn(3.5, 5.0)
                                        val minVal = (minRatingText.toDoubleOrNull() ?: 4.70).coerceIn(3.0, goodVal)
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(
                                                config.copy(
                                                    targetGoodPassengerRating = goodVal,
                                                    minAcceptablePassengerRating = minVal
                                                )
                                            )
                                            savedRatingFlag = true
                                            notifyUser("Limites de Nota do Passageiro salvos (🟢 >= $goodVal | 🟡 >= $minVal | 🔴 < $minVal)!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (savedRatingFlag) TrafficGreen else PetrolHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = LimeBright, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (savedRatingFlag) "✓ Notas do Passageiro Salvas!" else "Salvar Critérios de Nota do Passageiro",
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
                                            val simGood = NinetyNineOfferReader.parseScreenNodes(
                                                rawNodes = listOf(
                                                    "99Pop",
                                                    "R$ 22,00",
                                                    "★ 4,94 (85 corridas)",
                                                    "4 min (1,2 km) Av. Paulista, 900 - Bela Vista",
                                                    "14 min (5,8 km) Rua Vergueiro, 1800 - Vila Mariana"
                                                ),
                                                captureMethod = CaptureMethod.SIMULATION,
                                                isSimulated = true
                                            )
                                            val eval = FinancialEngine.evaluate(simGood, config)
                                            RuntimeDiagnosticsBus.publishEvaluation(eval)
                                            if (hasOverlayPermission) {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showEvaluation(eval)
                                            }
                                            CopilotNotificationReceiver.showActiveNotification(
                                                context = context,
                                                latestEvaluation = eval,
                                                showProfit = config.showProfitOnOverlay,
                                                showCost = config.showCostOnOverlay
                                            )
                                            notifyUser("Simulado Passageiro Nota BOA (🟢 ★ 4,94) na Janela Flutuante!")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TrafficGreen, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🟢 Testar ★ 4,94", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }

                                    Button(
                                        onClick = {
                                            val simBad = NinetyNineOfferReader.parseScreenNodes(
                                                rawNodes = listOf(
                                                    "99Pop",
                                                    "R$ 22,00",
                                                    "★ 4,55 (12 corridas)",
                                                    "4 min (1,2 km) Av. Paulista, 900 - Bela Vista",
                                                    "14 min (5,8 km) Rua Vergueiro, 1800 - Vila Mariana"
                                                ),
                                                captureMethod = CaptureMethod.SIMULATION,
                                                isSimulated = true
                                            )
                                            val eval = FinancialEngine.evaluate(simBad, config)
                                            RuntimeDiagnosticsBus.publishEvaluation(eval)
                                            if (hasOverlayPermission) {
                                                app.overlayPanel.applyConfig(config)
                                                app.overlayPanel.showEvaluation(eval)
                                            }
                                            CopilotNotificationReceiver.showActiveNotification(
                                                context = context,
                                                latestEvaluation = eval,
                                                showProfit = config.showProfitOnOverlay,
                                                showCost = config.showCostOnOverlay
                                            )
                                            notifyUser("Simulado Passageiro Nota RUIM (🔴 ★ 4,55) na Janela Flutuante!")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TrafficRed, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🔴 Testar ★ 4,55", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
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
                                    Text("Configurar Sobreposição", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
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
                                    Text("Configurar Acessibilidade", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
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
                                        Text(
                                            text = if (showAdvancedOcr) "Ocultar OCR" else "Modo OCR",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
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
                                        Text(
                                            text = "Simulador",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
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
                                        config = config,
                                        onOpenStreetView = { addr -> openStreetViewExternal(context, addr) },
                                        onMarkAddressAsRisk = promptMarkAddressAsRisk,
                                        onMarkAddressAsFavorite = promptMarkAddressAsFavorite,
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
                // TELA 8: SEGURANÇA E ÁREAS DE RISCO (NOVA TAB DEDICADA)
                // =====================================================
                GoOrNoScreen.SECURITY -> {
                    item {
                        // Seletor Superior de Abas (Áreas de Risco vs Regiões Favoritas)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (securitySelectedTab == 0) TrafficRed else SoftSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, if (securitySelectedTab == 0) TrafficRed else CleanCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { securitySelectedTab = 0 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (securitySelectedTab == 0) Color.White else TrafficRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Áreas de Risco",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.5.sp,
                                        color = if (securitySelectedTab == 0) Color.White else PetrolDarkText
                                    )
                                }
                            }

                            Surface(
                                color = if (securitySelectedTab == 1) Color(0xFF059669) else SoftSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, if (securitySelectedTab == 1) Color(0xFF059669) else CleanCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { securitySelectedTab = 1 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (securitySelectedTab == 1) Color.White else Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Regiões Favoritas",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.5.sp,
                                        color = if (securitySelectedTab == 1) Color.White else PetrolDarkText
                                    )
                                }
                            }
                        }

                        if (securitySelectedTab == 0) {
                        var searchAddressQuery by remember { mutableStateOf("") }
                        var isSearchingPlace by remember { mutableStateOf(false) }
                        var searchResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
                        var searchErrorMsg by remember { mutableStateOf<String?>(null) }

                        val customZoneList = remember(config.customRiskZones) {
                            RiskAreaEvaluator.parseCustomZonesList(config.customRiskZones)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.5.dp, TrafficRed.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = TrafficRed,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .padding(4.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Central de Segurança & Áreas de Risco",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = PetrolHeader
                                        )
                                        Text(
                                            text = "Validação geográfica no mapa e bloqueio preventivo de locais perigosos",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Switch 1: Ativar Alerta de Risco
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ativar Alerta de Área de Risco",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = "Mostra '⚠️ ÁREA DE RISCO' na Janela Flutuante e na Notificação",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.riskAlertEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(riskAlertEnabled = enabled))
                                                    notifyUser(if (enabled) "Alerta de Área de Risco ativado!" else "Alerta de Área de Risco desativado.")
                                                }
                                            }
                                        )
                                    }
                                }

                                // Switch 2: Forçar Semáforo VERMELHO
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Forçar Semáforo VERMELHO em Área de Risco",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = TrafficRed
                                            )
                                            Text(
                                                text = "Mesmo que pague bem por KM, avisa Recusar se for local perigoso",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.forceRedOnRiskArea,
                                            onCheckedChange = { forceRed ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(forceRedOnRiskArea = forceRed))
                                                    notifyUser(if (forceRed) "Semáforo forçará VERMELHO em áreas de risco!" else "Semáforo mostrará alerta mantendo avaliação.")
                                                }
                                            }
                                        )
                                    }
                                }

                                // Switch 3: Base Nacional + Termos Críticos
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Base Nacional de Zonas Críticas + Termos",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.5.sp,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = "Detecta automaticamente: viela, beco, favela, invasão, ocupação, comunidade + capitais",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.useDefaultRiskKeywords,
                                            onCheckedChange = { useDef ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(useDefaultRiskKeywords = useDef))
                                                    notifyUser(if (useDef) "Base nacional de risco ativada!" else "Usando apenas sua lista personalizada.")
                                                }
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // BUSCADOR DE ENDEREÇO ESTILO GOOGLE MAPS
                                Text(
                                    text = "🔍 Pesquisar e Validar Endereço no Mapa (Oficial)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Digite o nome da rua, comunidade ou bairro para validar a localização real no mapa antes de bloquear:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchAddressQuery,
                                        onValueChange = {
                                            searchAddressQuery = it
                                            searchErrorMsg = null
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Digite o nome da rua ou bairro...",
                                                fontSize = 12.5.sp,
                                                color = MutedText,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = PetrolHeader)
                                        },
                                        trailingIcon = {
                                            if (searchAddressQuery.isNotBlank()) {
                                                IconButton(onClick = {
                                                    searchAddressQuery = ""
                                                    searchResults = emptyList()
                                                    searchErrorMsg = null
                                                }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Search
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                val q = searchAddressQuery.trim().replace(",", "")
                                                if (q.length < 3) {
                                                    notifyUser("Digite pelo menos 3 letras para pesquisar no mapa.")
                                                } else {
                                                    isSearchingPlace = true
                                                    searchErrorMsg = null
                                                    scope.launch {
                                                        val places = PlaceSearchHelper.searchPlaces(context, q)
                                                        isSearchingPlace = false
                                                        if (places.isEmpty()) {
                                                            searchErrorMsg = "Nenhum endereço oficial localizado no mapa para '$q'. Você pode bloquear diretamente pelo nome abaixo."
                                                        } else {
                                                            searchResults = places
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            val q = searchAddressQuery.trim().replace(",", "")
                                            if (q.length < 3) {
                                                notifyUser("Digite pelo menos 3 letras para pesquisar no mapa.")
                                                return@Button
                                            }
                                            isSearchingPlace = true
                                            searchErrorMsg = null
                                            scope.launch {
                                                val places = PlaceSearchHelper.searchPlaces(context, q)
                                                isSearchingPlace = false
                                                if (places.isEmpty()) {
                                                    searchErrorMsg = "Nenhum endereço oficial localizado no mapa para '$q'. Você pode bloquear diretamente pelo nome abaixo."
                                                } else {
                                                    searchResults = places
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PetrolHeader,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        enabled = !isSearchingPlace
                                    ) {
                                        if (isSearchingPlace) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Localizando no Mapa...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = LimeBright, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Pesquisar Local no Mapa",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                if (searchErrorMsg != null) {
                                    Surface(
                                        color = Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, TrafficRed.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = searchErrorMsg!!,
                                                color = TrafficRed,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = {
                                                    val raw = searchAddressQuery.trim()
                                                    if (raw.length >= 3) {
                                                        val updatedList = (customZoneList + raw).distinct()
                                                        scope.launch {
                                                            app.preferencesRepository.saveConfig(
                                                                config.copy(customRiskZones = updatedList.joinToString(", "))
                                                            )
                                                            searchAddressQuery = ""
                                                            searchErrorMsg = null
                                                            searchResults = emptyList()
                                                            notifyUser("'$raw' bloqueado diretamente!")
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TrafficRed),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("+ Bloquear com este nome mesmo assim", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // RESULTADOS DA BUSCA NO MAPA
                                if (searchResults.isNotEmpty()) {
                                    Text(
                                        text = "📍 Locais encontrados no Mapa (Toque para confirmar o bloqueio):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PetrolHeader
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        searchResults.forEach { place ->
                                            Surface(
                                                color = SoftSurfaceVariant,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, CleanCardBorder),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "📍 ${place.title}",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 13.sp,
                                                                color = PetrolHeader
                                                            )
                                                            Text(
                                                                text = place.subtitle,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = PetrolDarkText
                                                            )
                                                            Text(
                                                                text = place.fullFormattedAddress,
                                                                fontSize = 10.5.sp,
                                                                color = MutedText
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                         Button(
                                                            onClick = {
                                                                val toBlock = place.termToBlock.trim().replace(",", "")
                                                                if (toBlock.length >= 3) {
                                                                    val updatedList = (customZoneList + toBlock).distinct()
                                                                    scope.launch {
                                                                        app.preferencesRepository.saveConfig(
                                                                            config.copy(customRiskZones = updatedList.joinToString(", "))
                                                                        )
                                                                        searchResults = emptyList()
                                                                        searchAddressQuery = ""
                                                                        notifyUser("Local validado '$toBlock' bloqueado com sucesso!")
                                                                    }
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = TrafficRed),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.weight(1f).height(38.dp)
                                                        ) {
                                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Bloquear Este Local",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }

                                                        FilledTonalButton(
                                                            onClick = {
                                                                try {
                                                                    val mapIntent = Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        Uri.parse("geo:0,0?q=" + Uri.encode(place.fullFormattedAddress))
                                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                                    context.startActivity(mapIntent)
                                                                } catch (_: Exception) {
                                                                    notifyUser("Não foi possível abrir o Google Maps.")
                                                                }
                                                            },
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = PureWhiteSurface,
                                                                contentColor = PetrolHeader
                                                            ),
                                                            border = BorderStroke(1.dp, CleanCardBorder),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.height(38.dp)
                                                        ) {
                                                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Ver no Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // LISTA DE LOCAIS BLOQUEADOS
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🛡️ Seus Locais Bloqueados (${customZoneList.size}):",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = PetrolHeader
                                    )
                                    if (customZoneList.isNotEmpty()) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(config.copy(customRiskZones = ""))
                                                notifyUser("Lista de locais bloqueados esvaziada.")
                                            }
                                        }) {
                                            Text("Limpar Todos", color = TrafficRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (customZoneList.isEmpty()) {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Nenhum local bloqueado manualmente ainda. Pesquise ruas ou bairros acima no mapa para adicionar à sua lista de segurança.",
                                            fontSize = 11.5.sp,
                                            color = MutedText,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        customZoneList.forEach { zone ->
                                            Surface(
                                                color = Color(0xFFFEF2F2),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, TrafficRed.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.Warning, contentDescription = null, tint = TrafficRed, modifier = Modifier.size(16.dp))
                                                        Text(
                                                            text = zone,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TrafficRed
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                try {
                                                                    val mapIntent = Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        Uri.parse("geo:0,0?q=" + Uri.encode(zone))
                                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                                    context.startActivity(mapIntent)
                                                                } catch (_: Exception) {}
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Map, contentDescription = "Ver no mapa", tint = PetrolHeader, modifier = Modifier.size(16.dp))
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                val remaining = customZoneList.filterNot { it.equals(zone, ignoreCase = true) }
                                                                scope.launch {
                                                                    app.preferencesRepository.saveConfig(
                                                                        config.copy(customRiskZones = remaining.joinToString(", "))
                                                                    )
                                                                    notifyUser("'$zone' removido da lista de segurança.")
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = TrafficRed, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Botão de teste na tela flutuante
                                Button(
                                    onClick = {
                                        val sampleRiskZone = customZoneList.firstOrNull() ?: "Capão Redondo"
                                        val testOffer = NinetyNineOfferReader.parseScreenNodes(
                                            rawNodes = listOf(
                                                "99Pop",
                                                "R$ 24,50",
                                                "★ 4,88 (34 corridas)",
                                                "4 min (1,2 km) Av. Paulista, 1000 - Bela Vista",
                                                "14 min (6,8 km) Viela 4, 120 - $sampleRiskZone"
                                            ),
                                            captureMethod = CaptureMethod.SIMULATION,
                                            isSimulated = true
                                        )
                                        val eval = FinancialEngine.evaluate(testOffer, config)
                                        RuntimeDiagnosticsBus.publishEvaluation(eval)
                                        if (hasOverlayPermission) {
                                            app.overlayPanel.applyConfig(config)
                                            app.overlayPanel.showEvaluation(eval)
                                        }
                                        CopilotNotificationReceiver.showActiveNotification(
                                            context = context,
                                            latestEvaluation = eval,
                                            showProfit = config.showProfitOnOverlay,
                                            showCost = config.showCostOnOverlay
                                        )
                                        notifyUser("Simulação de Área de Risco enviada para a Janela Flutuante e Notificação!")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF7F1D1D),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFEE2E2), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🚨 Testar Alerta de Risco na Janela",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        } else {
                        var searchFavQuery by remember { mutableStateOf("") }
                        var isSearchingFavPlace by remember { mutableStateOf(false) }
                        var searchFavResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
                        var searchFavErrorMsg by remember { mutableStateOf<String?>(null) }
                        val customFavList = remember(config.customFavoriteZones) {
                            FavoriteAreaEvaluator.parseCustomZonesList(config.customFavoriteZones)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF059669).copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF059669),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .padding(4.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Bairros Favoritos & Zona de Conforto",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = PetrolHeader
                                        )
                                        Text(
                                            text = "Destaque verde no card quando a corrida for para sua área preferida",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Switch: Ativar Destaque de Regiões Favoritas
                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CleanCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Destacar Bairros Favoritos",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = PetrolHeader
                                            )
                                            Text(
                                                text = "Exibe selo verde com estrela no card flutuante quando o destino ou embarque for na sua lista.",
                                                fontSize = 11.sp,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = config.favoriteZonesEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(config.copy(favoriteZonesEnabled = enabled))
                                                    notifyUser(if (enabled) "Destaque de Bairros Favoritos ativado!" else "Destaque de Bairros Favoritos pausado.")
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF059669)
                                            )
                                        )
                                    }
                                }

                                // Busca de Bairro/Região no Mapa
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "🔍 Pesquisar Bairro, Região ou Cidade no Mapa:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PetrolHeader
                                    )
                                    Text(
                                        text = "Digite o nome da região (Ex: Moema, Rudge Ramos, Centro, Jardins). Buscaremos no mapa com coordenadas exatas para destaque automático nas corridas.",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )

                                    OutlinedTextField(
                                        value = searchFavQuery,
                                        onValueChange = {
                                            searchFavQuery = it
                                            if (searchFavErrorMsg != null) searchFavErrorMsg = null
                                        },
                                        placeholder = {
                                            Text("Ex: Moema, Rudge Ramos, Centro...", fontSize = 12.sp, color = MutedText)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF059669))
                                        },
                                        trailingIcon = {
                                            if (searchFavQuery.isNotBlank()) {
                                                IconButton(onClick = {
                                                    searchFavQuery = ""
                                                    searchFavResults = emptyList()
                                                    searchFavErrorMsg = null
                                                }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Search
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                val q = searchFavQuery.trim().replace(",", "")
                                                if (q.length < 3) {
                                                    notifyUser("Digite pelo menos 3 letras para pesquisar no mapa.")
                                                } else {
                                                    isSearchingFavPlace = true
                                                    searchFavErrorMsg = null
                                                    scope.launch {
                                                        val places = PlaceSearchHelper.searchPlaces(context, q)
                                                        isSearchingFavPlace = false
                                                        if (places.isEmpty()) {
                                                            searchFavErrorMsg = "Nenhum endereço oficial localizado no mapa para '$q'. Você pode favoritar diretamente pelo nome abaixo."
                                                        } else {
                                                            searchFavResults = places
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            val q = searchFavQuery.trim().replace(",", "")
                                            if (q.length < 3) {
                                                notifyUser("Digite pelo menos 3 letras para pesquisar no mapa.")
                                                return@Button
                                            }
                                            isSearchingFavPlace = true
                                            searchFavErrorMsg = null
                                            scope.launch {
                                                val places = PlaceSearchHelper.searchPlaces(context, q)
                                                isSearchingFavPlace = false
                                                if (places.isEmpty()) {
                                                    searchFavErrorMsg = "Nenhum endereço oficial localizado no mapa para '$q'. Você pode favoritar diretamente pelo nome abaixo."
                                                } else {
                                                    searchFavResults = places
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        enabled = !isSearchingFavPlace
                                    ) {
                                        if (isSearchingFavPlace) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Localizando no Mapa...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = LimeBright, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Pesquisar Bairro no Mapa",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                if (searchFavErrorMsg != null) {
                                    Surface(
                                        color = Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, TrafficRed.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = searchFavErrorMsg!!,
                                                color = TrafficRed,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = {
                                                    val raw = searchFavQuery.trim()
                                                    if (raw.length >= 3) {
                                                        val updatedList = (customFavList + raw).distinct()
                                                        scope.launch {
                                                            app.preferencesRepository.saveConfig(
                                                                config.copy(customFavoriteZones = updatedList.joinToString(", "))
                                                            )
                                                            searchFavQuery = ""
                                                            searchFavErrorMsg = null
                                                            searchFavResults = emptyList()
                                                            notifyUser("⭐ '$raw' adicionado aos Favoritos diretamente!")
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("+ Adicionar com este nome mesmo assim", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // RESULTADOS DA BUSCA NO MAPA
                                if (searchFavResults.isNotEmpty()) {
                                    Text(
                                        text = "⭐ Bairros encontrados no Mapa (Toque para confirmar o favorito):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PetrolHeader
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        searchFavResults.forEach { place ->
                                            Surface(
                                                color = SoftSurfaceVariant,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, CleanCardBorder),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "⭐ ${place.title}",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 13.sp,
                                                                color = PetrolHeader
                                                            )
                                                            Text(
                                                                text = place.subtitle,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = PetrolDarkText
                                                            )
                                                            Text(
                                                                text = place.fullFormattedAddress,
                                                                fontSize = 10.5.sp,
                                                                color = MutedText
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                val toFav = place.termToBlock.trim().replace(",", "")
                                                                if (toFav.length >= 3) {
                                                                    val updatedList = (customFavList + toFav).distinct()
                                                                    scope.launch {
                                                                        app.preferencesRepository.saveConfig(
                                                                            config.copy(customFavoriteZones = updatedList.joinToString(", "))
                                                                        )
                                                                        searchFavResults = emptyList()
                                                                        searchFavQuery = ""
                                                                        notifyUser("⭐ Local validado '$toFav' adicionado aos Favoritos!")
                                                                    }
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.weight(1f).height(38.dp)
                                                        ) {
                                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Confirmar Bairro Favorito",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }

                                                        FilledTonalButton(
                                                            onClick = {
                                                                try {
                                                                    val mapIntent = Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        Uri.parse("geo:0,0?q=" + Uri.encode(place.fullFormattedAddress))
                                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                                    context.startActivity(mapIntent)
                                                                } catch (_: Exception) {
                                                                    notifyUser("Não foi possível abrir o Google Maps.")
                                                                }
                                                            },
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = PureWhiteSurface,
                                                                contentColor = PetrolHeader
                                                            ),
                                                            border = BorderStroke(1.dp, CleanCardBorder),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.height(38.dp)
                                                        ) {
                                                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Ver no Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Lista de Bairros Favoritos Cadastrados
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⭐ Seus Bairros Favoritos (${customFavList.size}):",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = PetrolHeader
                                    )
                                    if (customFavList.isNotEmpty()) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                app.preferencesRepository.saveConfig(config.copy(customFavoriteZones = ""))
                                                notifyUser("Lista de bairros favoritos esvaziada.")
                                            }
                                        }) {
                                            Text("Limpar Todos", color = TrafficRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (customFavList.isEmpty()) {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Nenhum bairro favorito cadastrado ainda. Digite os nomes dos bairros onde você mais gosta de trabalhar acima.",
                                            fontSize = 11.5.sp,
                                            color = MutedText,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        customFavList.forEach { zone ->
                                            Surface(
                                                color = Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                                        Text(
                                                            text = zone,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF065F46)
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                try {
                                                                    val mapIntent = Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        Uri.parse("geo:0,0?q=" + Uri.encode(zone))
                                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                                    context.startActivity(mapIntent)
                                                                } catch (_: Exception) {}
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Map, contentDescription = "Ver no mapa", tint = PetrolHeader, modifier = Modifier.size(16.dp))
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                val remaining = customFavList.filterNot { it.equals(zone, ignoreCase = true) }
                                                                scope.launch {
                                                                    app.preferencesRepository.saveConfig(
                                                                        config.copy(customFavoriteZones = remaining.joinToString(", "))
                                                                    )
                                                                    notifyUser("'$zone' removido dos bairros favoritos.")
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = TrafficRed, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Botão de teste na tela flutuante
                                Button(
                                    onClick = {
                                        val sampleFavZone = customFavList.firstOrNull() ?: "Moema"
                                        val testOffer = NinetyNineOfferReader.parseScreenNodes(
                                            rawNodes = listOf(
                                                "UberX",
                                                "R$ 28,50",
                                                "★ 4,96 (180 corridas)",
                                                "3 min (0,9 km) Av. Paulista, 1500 - Bela Vista",
                                                "16 min (8,2 km) R. Gaivota, 450 - $sampleFavZone"
                                            ),
                                            captureMethod = CaptureMethod.SIMULATION,
                                            isSimulated = true
                                        )
                                        val eval = FinancialEngine.evaluate(testOffer, config)
                                        RuntimeDiagnosticsBus.publishEvaluation(eval)
                                        if (hasOverlayPermission) {
                                            app.overlayPanel.applyConfig(config)
                                            app.overlayPanel.showEvaluation(eval)
                                        }
                                        CopilotNotificationReceiver.showActiveNotification(
                                            context = context,
                                            latestEvaluation = eval,
                                            showProfit = config.showProfitOnOverlay,
                                            showCost = config.showCostOnOverlay
                                        )
                                        notifyUser("Simulação de Bairro Favorito enviada para a Janela Flutuante!")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF065F46),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFA7F3D0), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⭐ Testar Bairro Favorito na Janela",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
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
                                    text = "Sincronização em Nuvem GoOrNo (100% Automática via WhatsApp)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = PetrolHeader
                                )
                                val savedWhatsApp = com.creativeam.goorno.data.license.LicenseManager.getSavedWhatsApp(context)
                                if (savedWhatsApp.isNotBlank()) {
                                    Surface(
                                        color = TrafficGreenSoft,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("☁️", fontSize = 18.sp)
                                            Column {
                                                Text(
                                                    text = "Vinculado ao WhatsApp: $savedWhatsApp",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = TrafficGreen
                                                )
                                                Text(
                                                    text = "Seus custos, metas e áreas de risco sincronizam automaticamente.",
                                                    fontSize = 11.sp,
                                                    color = PetrolDarkText
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "💡 Valide seu WhatsApp na aba 'Assinatura' para ativar o backup automático sem precisar digitar senhas ou códigos.",
                                            fontSize = 11.5.sp,
                                            color = MutedText,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Se você trocar de celular ou reinstalar o aplicativo, basta validar seu WhatsApp ou clicar em 'Restaurar da Nuvem' abaixo para recuperar seus custos, metas e histórico no mesmo instante.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = com.creativeam.goorno.data.cloud.CloudSyncManager.autoSyncSettings(
                                                    context = context,
                                                    config = config,
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
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sincronizar", fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val res = com.creativeam.goorno.data.cloud.CloudSyncManager.autoRestoreSettings(context)
                                                res.onSuccess { (restoredCfg, restoredOffers) ->
                                                    app.preferencesRepository.saveConfig(restoredCfg)
                                                    if (restoredOffers.isNotEmpty()) {
                                                        app.database.rideOfferDao().insertAll(restoredOffers)
                                                    }
                                                    notifyUser("Sucesso! Configurações restauradas da nuvem para este celular!")
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
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restaurar", fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }
                                }

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
                                        text = "Backup automático contínuo em segundo plano ativo",
                                        fontSize = 11.5.sp,
                                        color = PetrolDarkText
                                    )
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
                                    text = "Atualizações do Aplicativo (GoOrNo Auto-Update)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "O GoOrNo verifica atualizações automaticamente ao iniciar. Se houver uma nova versão oficial, um aviso surge na tela e você atualiza em 1 clique.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CleanCardBorder)
                                    ) {
                                        Text(
                                            text = "Versão: v$currentAppVersionName (Build $currentAppVersionCode)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PetrolDarkText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { checkForUpdates(true) },
                                        enabled = !isCheckingUpdates,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PetrolHeader,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isCheckingUpdates) "Verificando..." else "Verificar",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
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

                // =====================================================
                // TELA 11: ASSINATURA PRO, PIX E LICENÇA
                // =====================================================
                GoOrNoScreen.SUBSCRIPTION -> {
                    item {
                        SubscriptionScreenCard(onNotify = notifyUser)
                    }
                }
            }
        }
    }

    if (showUpdateDialog && updateInfoState != null) {
        val info = updateInfoState!!
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🚀",
                        fontSize = 20.sp
                    )
                    Column {
                        Text(
                            text = "Nova Atualização Disponível!",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = PetrolDarkText
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Surface(
                                color = TrafficGreenSoft,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "v${info.versionName} (Build ${info.versionCode})",
                                    color = TrafficGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            if (info.fileSizeFormatted.isNotBlank()) {
                                Text(
                                    text = info.fileSizeFormatted,
                                    fontSize = 10.5.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Confira as melhorias desta versão:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PetrolDarkText
                    )
                    Surface(
                        color = SoftSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (info.changelog.isNotEmpty()) {
                                info.changelog.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("✓", color = TrafficGreen, fontWeight = FontWeight.Black, fontSize = 10.5.sp)
                                        Text(
                                            text = item,
                                            fontSize = 10.sp,
                                            lineHeight = 12.5.sp,
                                            color = PetrolDarkText
                                        )
                                    }
                                }
                            } else {
                                Text("• Melhorias de estabilidade e precisão.", fontSize = 10.5.sp, color = PetrolDarkText)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val downloadUrl = if (info.apkUrl.isNotBlank()) info.apkUrl else "https://goorno-app.vercel.app/GoOrNo.apk"
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erro ao abrir link de download: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        showUpdateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrafficGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Baixar e Atualizar Agora",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Atualizar Depois",
                        color = MutedText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG 1: AJUSTAR FAIXAS DO SEMÁFORO (POPUP INTERATIVO)
    // -------------------------------------------------------------
    if (showAdjustFaixasDialog) {
        var tempMinKm by remember(config.minGrossPerKm) { mutableStateOf(config.minGrossPerKm) }
        var tempTargetKm by remember(config.targetGrossPerKm) { mutableStateOf(config.targetGrossPerKm) }
        var tempMinHr by remember(config.minGrossPerHour) { mutableStateOf(config.minGrossPerHour) }
        var tempTargetHr by remember(config.targetGrossPerHour) { mutableStateOf(config.targetGrossPerHour) }

        AlertDialog(
            onDismissRequest = { showAdjustFaixasDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🚦", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "Ajustar Faixas do Semáforo",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = PetrolDarkText
                        )
                        Text(
                            text = "Defina os limites de ganho para classificar as viagens",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Ganho por quilômetro (Imagem 1)
                    VisualDualTargetSliderCard(
                        title = "Ganho por quilômetro",
                        description = "Define quando o R$/km da corrida é ruim, OK ou bom.",
                        unitSuffix = "/km",
                        minValue = tempMinKm,
                        goodValue = tempTargetKm,
                        rangeMin = 1.0,
                        rangeMax = 8.0,
                        step = 0.10,
                        onMinChange = { tempMinKm = it },
                        onGoodChange = { tempTargetKm = it }
                    )

                    // 2. Ganho por hora (Imagem 1)
                    VisualDualTargetSliderCard(
                        title = "Ganho por hora",
                        description = "Define quando o R$/hora da corrida é ruim, OK ou bom.",
                        unitSuffix = "/h",
                        minValue = tempMinHr,
                        goodValue = tempTargetHr,
                        rangeMin = 20.0,
                        rangeMax = 150.0,
                        step = 5.0,
                        onMinChange = { tempMinHr = it },
                        onGoodChange = { tempTargetHr = it }
                    )

                    // 3. Resumo das Faixas ao vivo
                    Text(
                        text = "Resultado das Faixas:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = PetrolHeader
                    )
                    Surface(
                        color = PureWhiteSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CleanCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Critério", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MutedText, modifier = Modifier.weight(1.2f))
                                Text("🔴 Ruim", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficRed, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("🟡 Atenção", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficYellow, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                                Text("🟢 Aceitar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            HorizontalDivider(color = CleanCardBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("R$/km", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PetrolDarkText, modifier = Modifier.weight(1.2f))
                                Text("< ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", tempMinKm)}", fontSize = 10.sp, color = PetrolDarkText, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", tempMinKm)} - ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", tempTargetKm)}", fontSize = 10.sp, color = PetrolDarkText, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                                Text(">= ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", tempTargetKm)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("R$/hora", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PetrolDarkText, modifier = Modifier.weight(1.2f))
                                Text("< ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", tempMinHr)}", fontSize = 10.sp, color = PetrolDarkText, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", tempMinHr)} - ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", tempTargetHr)}", fontSize = 10.sp, color = PetrolDarkText, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                                Text(">= ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", tempTargetHr)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = config.copy(
                            minGrossPerKm = tempMinKm,
                            targetGrossPerKm = tempTargetKm,
                            minGrossPerHour = tempMinHr,
                            targetGrossPerHour = tempTargetHr,
                            yellowToleranceRatio = if (tempTargetKm > 0.0) (tempMinKm / tempTargetKm).coerceIn(0.5, 0.95) else config.yellowToleranceRatio
                        )
                        scope.launch {
                            app.preferencesRepository.saveConfig(updated)
                            app.overlayPanel.applyConfig(updated)
                            notifyUser("Faixas do Semáforo atualizadas com sucesso!")
                        }
                        showAdjustFaixasDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrafficGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Faixas", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAdjustFaixasDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar", color = MutedText, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG 2: CONFIRMAÇÃO DE CADASTRO DE ÁREA DE RISCO
    // -------------------------------------------------------------
    if (pendingRiskAddressToConfirm != null) {
        val targetZone = pendingRiskAddressToConfirm!!
        AlertDialog(
            onDismissRequest = { pendingRiskAddressToConfirm = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚠️", fontSize = 22.sp)
                    Text(
                        text = "Cadastrar Área de Risco?",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = PetrolDarkText
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Deseja realmente marcar este local como Área de Risco no GoOrNo?",
                        fontSize = 12.5.sp,
                        color = PetrolDarkText
                    )
                    Surface(
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, TrafficRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🚨 $targetZone",
                            color = TrafficRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        text = "Quando uma nova corrida for chamada para esta região, o copiloto exibirá um alerta vermelho em destaque na tela.",
                        fontSize = 11.5.sp,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentList = RiskAreaEvaluator.parseCustomZonesList(config.customRiskZones)
                        val updatedList = (currentList + targetZone).distinct()
                        scope.launch {
                            app.preferencesRepository.saveConfig(
                                config.copy(customRiskZones = updatedList.joinToString(", "))
                            )
                            notifyUser("⚠️ '$targetZone' cadastrado como Área de Risco!")
                        }
                        pendingRiskAddressToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrafficRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sim, Cadastrar Área de Risco", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRiskAddressToConfirm = null },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar", color = MutedText, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                }
            }
        )
    }

    if (pendingFavoriteAddressToConfirm != null) {
        val targetZone = pendingFavoriteAddressToConfirm!!
        AlertDialog(
            onDismissRequest = { pendingFavoriteAddressToConfirm = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF059669))
                    Text(
                        text = "Cadastrar Bairro Favorito",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = PetrolDarkText
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Deseja salvar este local como sua Região/Bairro Favorito no GoOrNo?",
                        fontSize = 12.5.sp,
                        color = PetrolDarkText
                    )
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⭐ $targetZone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Quando uma nova corrida for chamada para esta região ou com embarque nela, o card flutuante exibirá um destaque verde com estrela.",
                        fontSize = 11.5.sp,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentList = FavoriteAreaEvaluator.parseCustomZonesList(config.customFavoriteZones)
                        val updatedList = (currentList + targetZone).distinct()
                        scope.launch {
                            app.preferencesRepository.saveConfig(
                                config.copy(customFavoriteZones = updatedList.joinToString(", "))
                            )
                            notifyUser("⭐ '$targetZone' cadastrado como Bairro Favorito!")
                        }
                        pendingFavoriteAddressToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sim, Cadastrar Bairro Favorito", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingFavoriteAddressToConfirm = null },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar", color = MutedText, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                }
            }
        )
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
        border = BorderStroke(1.dp, if (granted) TrafficGreen else TrafficYellow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (granted) TrafficGreen else TrafficYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (granted) "✓" else number,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = PetrolDarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (granted) TrafficGreen else PetrolHeader,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (granted) "Ativo ✓" else "Ativar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MutedText,
                modifier = Modifier.fillMaxWidth()
            )
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

    val ratingColor = when (result.passengerRatingVerdict) {
        PassengerRatingVerdict.GOOD -> TrafficGreen
        PassengerRatingVerdict.MEDIUM -> TrafficYellow
        PassengerRatingVerdict.BAD -> TrafficRed
        PassengerRatingVerdict.UNKNOWN -> TrafficGray
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = verdictColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${result.verdict.label} — ${result.offer.sourceApp.displayName} (${result.offer.rideCategory ?: "Corrida"})",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (!result.passengerRatingBadge.isNullOrBlank()) {
                Surface(
                    color = ratingColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = result.passengerRatingBadge,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (result.hasRiskAlert && !result.riskAlertBadge.isNullOrBlank()) {
            Surface(
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.5.dp, TrafficRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🚨 ${result.riskAlertBadge}",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = TrafficRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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
    config: DriverFinancialConfig,
    onOpenStreetView: (String) -> Unit,
    onMarkAddressAsRisk: (String) -> Unit,
    onMarkAddressAsFavorite: (String) -> Unit = {},
    onUpdateReceivedPrice: (Double) -> Unit
) {
    var showAuditInput by remember { mutableStateOf(false) }
    var paidText by remember { mutableStateOf(entity.actualReceivedPrice?.toString() ?: entity.offeredPrice.toString()) }
    val dateStr = remember(entity.timestampMillis) {
        SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(entity.timestampMillis))
    }

    val riskCheck = remember(entity.originAddress, entity.destinationAddress, config) {
        RiskAreaEvaluator.evaluate(entity.originAddress, entity.destinationAddress, config)
    }

    val favCheck = remember(entity.originAddress, entity.destinationAddress, config) {
        FavoriteAreaEvaluator.evaluate(entity.originAddress, entity.destinationAddress, config)
    }

    val badgeColor = when (entity.verdict) {
        TrafficLightVerdict.GREEN.name -> TrafficGreen
        TrafficLightVerdict.YELLOW.name -> TrafficYellow
        TrafficLightVerdict.RED.name -> TrafficRed
        else -> TrafficGray
    }

    val totalKmStr = entity.totalDistanceKm?.let {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
    } ?: "-- km"
    val totalMinStr = entity.totalDurationMin?.let { "$it min" } ?: "-- min"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // LINHA 1: CABEÇALHO COM APP, VEREDITO E DATA/HORA SEM QUEBRA VERTICAL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (entity.sourceApp.contains("99", ignoreCase = true)) Color(0xFFFF9800) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = entity.sourceApp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = when (entity.verdict) {
                        TrafficLightVerdict.GREEN.name -> "🟢 Excelente"
                        TrafficLightVerdict.YELLOW.name -> "🟡 Atenção"
                        TrafficLightVerdict.RED.name -> "🔴 Não Compensa"
                        else -> "⚪ Neutro"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PetrolDarkText
                )
            }
            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = MutedText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }

        // LINHA 2: BANNER COLORIDO EM LARGURA TOTAL COM VALOR E DISTÂNCIA/TEMPO
        Surface(
            color = badgeColor,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", entity.offeredPrice),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Text(
                    text = "$totalKmStr  •  $totalMinStr",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = Color.White
                )
            }
        }

        if (riskCheck.hasRisk) {
            Surface(
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, TrafficRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🚨 ${riskCheck.shortBadge}",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.5.sp,
                    color = TrafficRed,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        if (favCheck.isFavorite) {
            Surface(
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFF059669)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⭐ REGIÃO FAVORITA: ${favCheck.shortBadge}",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.5.sp,
                    color = Color(0xFF065F46),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // 3 BLOCOS DE MÉTRICAS ALINHADAS E ELEGANTES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = SoftSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("KM BRUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText)
                    Text(
                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", entity.grossPerKm ?: 0.0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = PetrolHeader,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Surface(
                color = SoftSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("HORA BRUTA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedText)
                    Text(
                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", entity.grossPerHour ?: 0.0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = PetrolHeader,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Surface(
                color = if ((entity.estimatedNetProfit ?: 0.0) >= 0.0) TrafficGreenSoft else Color(0xFFFEF2F2),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.15f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LUCRO ESTIM.",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if ((entity.estimatedNetProfit ?: 0.0) >= 0.0) TrafficGreen else TrafficRed
                    )
                    Text(
                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", entity.estimatedNetProfit ?: 0.0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = if ((entity.estimatedNetProfit ?: 0.0) >= 0.0) TrafficGreen else TrafficRed,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Exibe o motivo financeiro ou alerta de segurança quando o veredito for Atenção ou Recusar
        val finReasons = entity.reasonsSummary.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("Passageiro Nota", ignoreCase = true) }
        if (finReasons.isNotEmpty()) {
            val isRedVerd = entity.verdict.contains("RED", ignoreCase = true) || entity.verdict.contains("Recusar", ignoreCase = true)
            val isYellowVerd = entity.verdict.contains("YELLOW", ignoreCase = true) || entity.verdict.contains("Atenção", ignoreCase = true) || entity.verdict.contains("Avaliar", ignoreCase = true)
            if (isRedVerd || isYellowVerd) {
                val bColor = if (isRedVerd) TrafficRed else TrafficYellow
                Surface(
                    color = bColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, bColor.copy(alpha = 0.40f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        finReasons.take(2).forEach { rMsg ->
                            val icn = if (rMsg.contains("risco", ignoreCase = true)) "⚠️" else if (isRedVerd) "🔴" else "🟡"
                            Text(
                                text = "$icn $rMsg",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = bColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        if (entity.reasonsSummary.contains("Passageiro Nota", ignoreCase = true)) {
            val ratingLine = entity.reasonsSummary.split("|")
                .map { it.trim() }
                .firstOrNull { it.contains("Passageiro Nota", ignoreCase = true) }
            if (ratingLine != null) {
                val rColor = when {
                    ratingLine.contains("🟢") || ratingLine.contains("Boa", ignoreCase = true) -> TrafficGreen
                    ratingLine.contains("🟡") || ratingLine.contains("Média", ignoreCase = true) -> TrafficYellow
                    else -> TrafficRed
                }
                val conciseRating = when {
                    ratingLine.contains("Boa", ignoreCase = true) -> {
                        val score = Regex("""[\d.,]+""").find(ratingLine)?.value ?: ""
                        "🟢 Nota Passageiro: ★ $score (Boa)"
                    }
                    ratingLine.contains("Média", ignoreCase = true) -> {
                        val score = Regex("""[\d.,]+""").find(ratingLine)?.value ?: ""
                        "🟡 Nota Passageiro: ★ $score (Atenção)"
                    }
                    ratingLine.contains("Ruim", ignoreCase = true) -> {
                        val score = Regex("""[\d.,]+""").find(ratingLine)?.value ?: ""
                        "🔴 Nota Passageiro: ★ $score (Baixa)"
                    }
                    else -> ratingLine.take(45)
                }
                Surface(
                    color = rColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, rColor.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = conciseRating,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = rColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // EXIBIÇÃO DO ENDEREÇO DE ORIGEM (100% de largura para o texto e botões em linha dedicada)
        Surface(
            color = SoftSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CleanCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📍 ORIGEM (EMBARQUE)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = PetrolHeader,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = entity.originAddress?.takeIf { it.isNotBlank() } ?: "Endereço não informado na oferta",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PetrolDarkText,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!entity.originAddress.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMarkAddressAsFavorite(entity.originAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⭐ +Favorito",
                                    color = Color(0xFF065F46),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, TrafficRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMarkAddressAsRisk(entity.originAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️ +Risco",
                                    color = TrafficRed,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = PetrolHeader,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenStreetView(entity.originAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌐 360° Maps",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // EXIBIÇÃO DO ENDEREÇO DE DESTINO (100% de largura para o texto e botões em linha dedicada)
        Surface(
            color = TrafficGreenSoft.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TrafficGreen.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🏁 DESTINO (DESEMBARQUE)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = PetrolHeader,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = entity.destinationAddress?.takeIf { it.isNotBlank() } ?: "Endereço não informado na oferta",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PetrolDarkText,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!entity.destinationAddress.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMarkAddressAsFavorite(entity.destinationAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⭐ +Favorito",
                                    color = Color(0xFF065F46),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, TrafficRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMarkAddressAsRisk(entity.destinationAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️ +Risco",
                                    color = TrafficRed,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = PetrolHeader,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenStreetView(entity.destinationAddress) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌐 360° Maps",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

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
                        "✅ Pago: R$ %.2f (%s)",
                        entity.actualReceivedPrice,
                        if (diff < -0.05) "Menor!" else "Valor OK ✓"
                    ),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (diff < -0.05) TrafficRed else TrafficGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Conferência do valor recebido:",
                    fontSize = 11.sp,
                    color = MutedText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { showAuditInput = !showAuditInput },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SoftSurfaceVariant,
                    contentColor = PetrolHeader
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = if (entity.actualReceivedPrice != null) "Editar" else "Conferir",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
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

@Composable
fun VisualDualTargetSliderCard(
    title: String,
    description: String,
    unitSuffix: String,
    minValue: Double,
    goodValue: Double,
    rangeMin: Double,
    rangeMax: Double,
    step: Double,
    onMinChange: (Double) -> Unit,
    onGoodChange: (Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = PetrolDarkText
                )
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = MutedText
                )
            }

            // Dois Quadros Lado a Lado (Ruim até vs Bom a partir de) - Fiel à Imagem 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Caixa 1: Ruim até (Borda âmbar/amarela)
                Surface(
                    color = SoftSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFEAB308)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Ruim até",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", minValue),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = PetrolDarkText
                            )
                            Text(
                                text = unitSuffix,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val n = ((minValue - step) * 100).roundToInt() / 100.0
                                    onMinChange(n.coerceAtLeast(rangeMin))
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val n = ((minValue + step) * 100).roundToInt() / 100.0
                                    if (n < goodValue) onMinChange(n)
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Caixa 2: Bom a partir de (Borda verde)
                Surface(
                    color = SoftSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF22C55E)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Bom a partir de",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", goodValue),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = TrafficGreen
                            )
                            Text(
                                text = unitSuffix,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val n = ((goodValue - step) * 100).roundToInt() / 100.0
                                    if (n > minValue) onGoodChange(n)
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val n = ((goodValue + step) * 100).roundToInt() / 100.0
                                    onGoodChange(n.coerceAtMost(rangeMax))
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Slider Visual com Marcadores (Pins) Vermelho e Verde (Fiel à Imagem 1)
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Ruim", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TrafficRed)
                    Text(text = "Bom", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TrafficGreen)
                }

                Spacer(modifier = Modifier.height(4.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    val totalWidth = maxWidth
                    val minFrac = ((minValue - rangeMin) / (rangeMax - rangeMin)).toFloat().coerceIn(0.04f, 0.88f)
                    val goodFrac = ((goodValue - rangeMin) / (rangeMax - rangeMin)).toFloat().coerceIn(minFrac + 0.06f, 0.96f)

                    // Linha Gradiente de Fundo (Vermelho -> Amarelo -> Verde)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFEF4444),
                                        Color(0xFFFACC15),
                                        Color(0xFF22C55E)
                                    )
                                )
                            )
                    )

                    // Pin Vermelho (Marcador "Ruim até")
                    val minOffset = (totalWidth - 20.dp) * minFrac
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(x = minOffset)
                            .align(Alignment.TopStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .shadow(3.dp, CircleShape)
                                .background(Color(0xFFEF4444), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(10.dp)
                                .background(Color(0xFFEF4444))
                        )
                    }

                    // Pin Verde (Marcador "Bom a partir de")
                    val goodOffset = (totalWidth - 20.dp) * goodFrac
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(x = goodOffset)
                            .align(Alignment.TopStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .shadow(3.dp, CircleShape)
                                .background(Color(0xFF22C55E), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(10.dp)
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuasFaixasMatrixCard(
    config: DriverFinancialConfig,
    onAdjustClick: () -> Unit
) {
    val yellowRatio = config.yellowToleranceRatio.coerceIn(0.5, 0.95)
    val kmMinYellow = config.targetGrossPerKm * yellowRatio
    val kmMinGreen = config.targetGrossPerKm
    val hrMinYellow = config.targetGrossPerHour * yellowRatio
    val hrMinGreen = config.targetGrossPerHour
    val ratingMinYellow = config.minAcceptablePassengerRating
    val ratingMinGreen = config.targetGoodPassengerRating

    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Suas Faixas",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = PetrolHeader
                    )
                    Text(
                        text = "Confira os valores de cada faixa do semáforo.",
                        fontSize = 11.5.sp,
                        color = MutedText
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CleanCardBorder),
                    color = SoftSurfaceVariant,
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable { onAdjustClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = PetrolHeader,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Ajustar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PetrolHeader
                        )
                    }
                }
            }

            // Tabela / Matriz de Faixas (FAIXA | Ruim | Atenção | Aceitar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            ) {
                // Header da Tabela
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FAIXA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF64748B),
                        modifier = Modifier.weight(1.1f)
                    )
                    Row(modifier = Modifier.weight(0.95f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.size(7.dp).background(TrafficRed, CircleShape))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Ruim", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficRed)
                    }
                    Row(modifier = Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.size(7.dp).background(TrafficYellow, CircleShape))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Atenção", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficYellow)
                    }
                    Row(modifier = Modifier.weight(0.95f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.size(7.dp).background(TrafficGreen, CircleShape))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Aceitar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrafficGreen)
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Linha 1: R$/km
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("R$/km", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = PetrolDarkText)
                        Text("por km", fontSize = 9.sp, color = MutedText)
                    }
                    Text(
                        text = "< ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", kmMinYellow)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", kmMinYellow)} - ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", kmMinGreen)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "≥ ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", kmMinGreen)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrafficGreen,
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Linha 2: R$/hora
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("R$/hora", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = PetrolDarkText)
                        Text("por hora", fontSize = 9.sp, color = MutedText)
                    }
                    Text(
                        text = "< ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", hrMinYellow)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", hrMinYellow)} - ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", hrMinGreen)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "≥ ${String.format(Locale.forLanguageTag("pt-BR"), "%.0f", hrMinGreen)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrafficGreen,
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Linha 3: Nota passageiro
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("Nota", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = PetrolDarkText)
                        Text("passageiro", fontSize = 9.sp, color = MutedText)
                    }
                    Text(
                        text = "< ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratingMinYellow)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratingMinYellow)} - ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratingMinGreen)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                    Text(
                        text = "≥ ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ratingMinGreen)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrafficGreen,
                        modifier = Modifier.weight(0.95f),
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun openStreetViewExternal(context: Context, address: String) {
    StreetViewLauncher.openDirectStreetView(context, address)
}

@Composable
private fun ModernToolCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        shadowElevation = 1.5.dp,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 20.sp)
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = PetrolDarkText
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickHubActionCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    badgeText: String,
    badgeColor: Color,
    bgColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 22.sp)
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PetrolDarkText,
                maxLines = 1,
                softWrap = false
            )
            Surface(
                color = badgeColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun QuickHubListActionRow(
    icon: String,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = PetrolHeader,
    onClick: () -> Unit
) {
    Surface(
        color = PureWhiteSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SoftSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = icon, fontSize = 17.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PetrolDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (!badge.isNullOrBlank()) {
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(3.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MutedText.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EarningsPillTabSelector(
    currentScreen: GoOrNoScreen,
    onSelectScreen: (GoOrNoScreen) -> Unit
) {
    Surface(
        color = SoftSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CleanCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isSemaforo = currentScreen == GoOrNoScreen.FINANCIAL_TARGETS
            Surface(
                color = if (isSemaforo) PetrolHeader else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectScreen(GoOrNoScreen.FINANCIAL_TARGETS) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚦 Semáforo & Metas",
                        fontSize = 12.sp,
                        fontWeight = if (isSemaforo) FontWeight.Black else FontWeight.Bold,
                        color = if (isSemaforo) Color.White else PetrolDarkText
                    )
                }
            }
            val isCustos = currentScreen == GoOrNoScreen.VEHICLE_COSTS
            Surface(
                color = if (isCustos) PetrolHeader else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectScreen(GoOrNoScreen.VEHICLE_COSTS) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⛽ Custos do Carro",
                        fontSize = 12.sp,
                        fontWeight = if (isCustos) FontWeight.Black else FontWeight.Bold,
                        color = if (isCustos) Color.White else PetrolDarkText
                    )
                }
            }
        }
    }
}


