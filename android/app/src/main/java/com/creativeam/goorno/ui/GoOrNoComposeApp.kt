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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import android.graphics.Typeface
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
import java.util.Calendar
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
    FINANCIAL_TARGETS(2, "Metas & Semáforo", "Metas"),
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
    SUBSCRIPTION(12, "Assinatura Pro e Licença", "Plano Pro"),
    CONTACT_SUPPORT(13, "Fale Conosco", "Suporte")
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
    onStopAllBackgroundServices: () -> Unit,
    resumeTrigger: Int = 0
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as GoOrNoApplication }
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(GoOrNoScreen.HOME) }

    val config by app.preferencesRepository.configFlow.collectAsState(initial = DriverFinancialConfig())
    val realHistory by app.database.rideOfferDao().observeRealOffers().collectAsState(initial = emptyList())

    val runtimeState by RuntimeDiagnosticsBus.runtimeState.collectAsState()
    val accessibilityConnected by RuntimeDiagnosticsBus.accessibilityConnected.collectAsState()
    val ocrActive by RuntimeDiagnosticsBus.ocrActive.collectAsState()
    val lastSnapshot = if (currentScreen == GoOrNoScreen.DIAGNOSTICS_TESTS || currentScreen == GoOrNoScreen.CONNECTION_STATUS) {
        RuntimeDiagnosticsBus.lastSnapshot.collectAsState().value
    } else {
        RuntimeDiagnosticsBus.lastSnapshot.value
    }
    val lastRealEvaluation by RuntimeDiagnosticsBus.lastRealEvaluation.collectAsState()
    val lastSimulatedEvaluation by RuntimeDiagnosticsBus.lastSimulatedEvaluation.collectAsState()
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

    val versionPrefs = remember { context.getSharedPreferences("goorno_app_version_prefs", Context.MODE_PRIVATE) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lastSeenCode = versionPrefs.getInt("last_seen_version_code", 0)
        if (lastSeenCode != 0 && currentAppVersionCode > lastSeenCode) {
            showWhatsNewDialog = true
        } else if (lastSeenCode == 0) {
            versionPrefs.edit().putInt("last_seen_version_code", currentAppVersionCode).apply()
        }
    }

    var updateInfoState by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    var showAdjustFaixasDialog by remember { mutableStateOf(false) }
    var pendingRiskAddressToConfirm by remember { mutableStateOf<String?>(null) }
    var pendingFavoriteAddressToConfirm by remember { mutableStateOf<String?>(null) }
    var securitySelectedTab by remember { mutableStateOf(0) }
    var histAppFilter by remember { mutableStateOf("TODOS") }
    var histStatusFilter by remember { mutableStateOf("TODAS") }
    var histSearchQuery by remember { mutableStateOf("") }
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    val filteredHistory = remember(realHistory, histAppFilter, histStatusFilter, histSearchQuery) {
        realHistory.filter { item ->
            val matchesApp = when (histAppFilter) {
                "UBER" -> item.sourceApp.contains("Uber", ignoreCase = true)
                "99" -> item.sourceApp.contains("99", ignoreCase = true)
                else -> true
            }
            val isAccepted = item.actualReceivedPrice != null && item.actualReceivedPrice > 0.0
            val isGreen = item.verdict.contains("GREEN", ignoreCase = true) || item.verdict.contains("Excelente", ignoreCase = true)
            val matchesStatus = when (histStatusFilter) {
                "ACEITAS" -> isAccepted || isGreen
                "RECUSADAS" -> !isAccepted && !isGreen
                "CONFIRMAR" -> item.actualReceivedPrice == null
                else -> true
            }
            val matchesSearch = if (histSearchQuery.isBlank()) true else {
                val q = histSearchQuery.trim().replace(",", ".")
                val pStr = String.format(Locale.US, "%.2f", item.offeredPrice)
                pStr.contains(q) || item.originAddress?.contains(q, ignoreCase = true) == true || item.destinationAddress?.contains(q, ignoreCase = true) == true
            }
            matchesApp && matchesStatus && matchesSearch
        }
    }
    var showAccessibilityDisclosureDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showLegalNatureDialog by remember { mutableStateOf(false) }

    val promptMarkAddressAsRisk: (String) -> Unit = { rawAddr ->
        val cleanTerm = rawAddr.trim()
        if (cleanTerm.length >= 3) {
            pendingRiskAddressToConfirm = cleanTerm
        } else {
            Toast.makeText(context, "Endereço muito curto para cadastrar como risco.", Toast.LENGTH_SHORT).show()
        }
    }

    val promptMarkAddressAsFavorite: (String) -> Unit = { rawAddr ->
        val cleanTerm = rawAddr.trim()
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
                    connectTimeout = 8000
                    readTimeout = 8000
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
        scope.launch(Dispatchers.IO) {
            try {
                val savedPhone = com.creativeam.goorno.data.license.LicenseManager.getSavedWhatsApp(context)
                if (savedPhone.length >= 10) {
                    val currentCfg = app.preferencesRepository.getConfigOnce()
                    if (currentCfg.customRiskZones.isBlank() && currentCfg.customFavoriteZones.isBlank()) {
                        val res = com.creativeam.goorno.data.cloud.CloudSyncManager.autoRestoreSettings(context, savedPhone)
                        res.onSuccess { (cloudCfg, cloudOffers) ->
                            if (cloudCfg.customRiskZones.isNotBlank() || cloudCfg.customFavoriteZones.isNotBlank()) {
                                app.preferencesRepository.saveConfig(cloudCfg)
                                if (cloudOffers.isNotEmpty()) {
                                    app.database.rideOfferDao().insertAll(cloudOffers)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger > 0) {
            checkForUpdates(false)
        }
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
                "CONTACT", "CONTACT_SUPPORT", "SUPORTE", "FALE_CONOSCO" -> currentScreen = GoOrNoScreen.CONTACT_SUPPORT
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
                            GoOrNoScreen.DRIVER_SETUP,
                        GoOrNoScreen.SECURITY,
                        GoOrNoScreen.SERVICES_ACTIVATION,
                        GoOrNoScreen.CONNECTION_STATUS,
                        GoOrNoScreen.DIAGNOSTICS_TESTS,
                        GoOrNoScreen.PRIVACY_BACKUP,
                        GoOrNoScreen.SUBSCRIPTION,
                        GoOrNoScreen.CONTACT_SUPPORT
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
                                    text = if (currentScreen == GoOrNoScreen.SECURITY) {
                                        if (securitySelectedTab == 1) "Bairros & Favoritos" else "Áreas de Risco"
                                    } else currentScreen.shortTab,
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
                            GoOrNoScreen.SUBSCRIPTION,
                            GoOrNoScreen.CONTACT_SUPPORT
                        )
                        val quickTabs = listOf(
                            Triple(GoOrNoScreen.HOME, Icons.Default.Dashboard, "Início"),
                            Triple(GoOrNoScreen.HISTORY, Icons.Default.History, "Histórico"),
                            Triple(GoOrNoScreen.FINANCIAL_TARGETS, Icons.Default.Tune, "Metas"),
                            Triple(GoOrNoScreen.SETTINGS, Icons.Default.Settings, "Ajustes")
                        )
                        quickTabs.forEach { (screen, icon, label) ->
                            val selected = when (screen) {
                                GoOrNoScreen.HOME -> currentScreen == GoOrNoScreen.HOME
                                GoOrNoScreen.HISTORY -> currentScreen == GoOrNoScreen.HISTORY
                                GoOrNoScreen.FINANCIAL_TARGETS -> currentScreen == GoOrNoScreen.FINANCIAL_TARGETS || currentScreen == GoOrNoScreen.VEHICLE_COSTS
                                GoOrNoScreen.SETTINGS -> currentScreen == GoOrNoScreen.SETTINGS || isSubScreenOfSettings || currentScreen == GoOrNoScreen.DRIVER_SETUP
                                else -> false
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        currentScreen = screen
                                    }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) PetrolHeader.copy(alpha = 0.12f) else Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (selected) PetrolHeader else MutedText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
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
                            GoOrNoScreen.CONTACT_SUPPORT -> "Canal direto de Caixa Postal com o desenvolvedor"
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
                    val isPermsReady = hasOverlayPermission && (hasAccessibilityPermission || ocrActive)
                    val isCopilotActive = isPermsReady && config.copilotMonitoringEnabled
                    val riskCount = config.customRiskZones.split("\n", ";").count { it.isNotBlank() }

                    // 1. BANNER DE PERMISSÕES PENDENTES (Alerta limpo no topo apenas se faltar algo)
                    if (!isPermsReady) {
                        item {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentScreen = GoOrNoScreen.SERVICES_ACTIVATION }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Permissões pendentes para leitura automática",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E)
                                        )
                                        Text(
                                            text = "Toque aqui para ativar Sobreposição e Acessibilidade",
                                            fontSize = 11.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Text(">", fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                }
                            }
                        }
                    }

                    // 2. CARD 1: COPILOTO (VISÍVEL FORA DO APP)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = if (isCopilotActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (isCopilotActive) "✨" else "💤", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Copiloto",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = PetrolDarkText
                                        )
                                        Text(
                                            text = if (isCopilotActive) "Visível fora do app • Pronto" else "Pausado • Toque para ativar",
                                            fontSize = 11.5.sp,
                                            color = if (isCopilotActive) TrafficGreen else MutedText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Ajustes do Copiloto",
                                            tint = MutedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = config.copilotMonitoringEnabled,
                                        onCheckedChange = { checked ->
                                            if (!hasOverlayPermission) {
                                                onRequestOverlayPermission()
                                            } else if (!hasAccessibilityPermission) {
                                                showAccessibilityDisclosureDialog = true
                                            } else {
                                                val updated = config.copy(copilotMonitoringEnabled = checked)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    app.overlayPanel.setMonitoringEnabled(checked, hasAccessibilityPermission)
                                                    if (!checked) {
                                                        CopilotNotificationReceiver.cancelActiveNotification(context)
                                                        onStopAllBackgroundServices()
                                                        notifyUser("Copiloto desativado")
                                                    } else {
                                                        CopilotNotificationReceiver.showActiveNotification(context)
                                                        notifyUser("Copiloto ativado")
                                                    }
                                                }
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
                        }
                    }

                    // 3. CARD 2: SEMÁFORO DE VALORES (UBER & 99)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
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
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🚦", fontSize = 20.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Semáforo de Valores",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                color = PetrolDarkText
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Surface(
                                                    color = Color(0xFFFACC15),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "99",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp,
                                                        color = Color.Black,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Surface(
                                                    color = Color(0xFF0F172A),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Uber",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { currentScreen = GoOrNoScreen.FINANCIAL_TARGETS },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Configurar Semáforo",
                                                tint = MutedText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = isCopilotActive,
                                            onCheckedChange = { checked ->
                                                if (!hasOverlayPermission) {
                                                    onRequestOverlayPermission()
                                                } else if (!hasAccessibilityPermission) {
                                                    showAccessibilityDisclosureDialog = true
                                                } else {
                                                    val updated = config.copy(copilotMonitoringEnabled = checked)
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.setMonitoringEnabled(checked, hasAccessibilityPermission)
                                                        if (!checked) {
                                                            CopilotNotificationReceiver.cancelActiveNotification(context)
                                                            onStopAllBackgroundServices()
                                                            notifyUser("Semáforo pausado")
                                                        } else {
                                                            CopilotNotificationReceiver.showActiveNotification(context)
                                                            notifyUser("Semáforo monitorando")
                                                        }
                                                    }
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

                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdjustFaixasDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(TrafficGreen, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Verde a partir de R$ ${String.format(Locale.forLanguageTag("pt-BR"), "%.2f", config.minGrossPerKm)}/km · R$ ${config.minGrossPerHour.toInt()}/h",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                        Text(">", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF15803D))
                                    }
                                }
                            }
                        }
                    }

                    // 4. CARD 3: ÁREAS DE RISCO & SEGURANÇA
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
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
                                            color = Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🛡️", fontSize = 20.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Áreas de Risco",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                color = PetrolDarkText
                                            )
                                            Text(
                                                text = "Alertas no embarque e destino",
                                                fontSize = 11.5.sp,
                                                color = MutedText
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { currentScreen = GoOrNoScreen.SECURITY },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Configurar Segurança",
                                                tint = MutedText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = config.riskAlertEnabled,
                                            onCheckedChange = { checked ->
                                                val updated = config.copy(riskAlertEnabled = checked)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    notifyUser(if (checked) "Áreas de Risco ativadas" else "Áreas de Risco pausadas")
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = TrafficRed,
                                                uncheckedThumbColor = Color.White,
                                                uncheckedTrackColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = Color(0xFFFFF1F2),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentScreen = GoOrNoScreen.SECURITY }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(TrafficRed, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (riskCount > 0) "$riskCount bairros cadastrados para alerta" else "Nenhum bairro cadastrado",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C)
                                            )
                                        }
                                        Text("Gerenciar >", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFFB91C1C))
                                    }
                                }
                            }
                        }
                    }

                    // 5. CARD 4: METAS DO MOTORISTA
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
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
                                            color = Color(0xFFEFF6FF),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🎯", fontSize = 20.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Metas de Faturamento",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                color = PetrolDarkText
                                            )
                                            Text(
                                                text = "Rentabilidade líquida e custos",
                                                fontSize = 11.5.sp,
                                                color = MutedText
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { currentScreen = GoOrNoScreen.FINANCIAL_TARGETS },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Abrir Metas",
                                            tint = PetrolHeader,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = Color(0xFFF0F9FF),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentScreen = GoOrNoScreen.FINANCIAL_TARGETS }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Configurar metas e custos do carro",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                        Text(">", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0369A1))
                                    }
                                }
                            }
                        }
                    }

                    // 6. ÚLTIMA OFERTA REAL DETECTADA (Se existir)
                    item {
                        val latestEval = lastRealEvaluation
                        val latestDbOffer = realHistory.firstOrNull()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ÚLTIMA CORRIDA ANALISADA",
                                    fontSize = 12.sp,
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
                                                if (newVal != null) {
                                                    notifyUser("Corrida marcada como Aceita!")
                                                } else {
                                                    notifyUser("Corrida desmarcada!")
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "Nenhuma chamada foi capturada ainda. Quando tocar uma corrida na Uber ou 99, ela aparecerá automaticamente.",
                                        fontSize = 12.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }
                    }
                }

                // =====================================================
                // TELA 2: ESTILO DO CARTÃO & ATALHOS FLUTUANTES
                // =====================================================
                
                // =====================================================
                // TELA 4: AJUSTES DO APLICATIVO (CENTRAL DE CONFIGURAÇÕES)
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
                                    text = "PERSONALIZAÇÃO & ESTILO",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PetrolHeader
                                )

                                QuickHubListActionRow(
                                    icon = "🎨",
                                    title = "Estilo do Cartão",
                                    subtitle = "Tema, formato (completo/secundário) e posição",
                                    badge = "Visual",
                                    badgeColor = PetrolHeader,
                                    onClick = { currentScreen = GoOrNoScreen.DRIVER_SETUP }
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = CleanCardBorder)
                                Spacer(modifier = Modifier.height(4.dp))

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

                                QuickHubListActionRow(
                                    icon = "✉️",
                                    title = "Fale Conosco / Suporte",
                                    subtitle = "Contato direto com o suporte GoOrNo",
                                    badge = "Direto",
                                    badgeColor = TrafficGreen,
                                    onClick = { currentScreen = GoOrNoScreen.CONTACT_SUPPORT }
                                )

                                QuickHubListActionRow(
                                    icon = "⚖️",
                                    title = "Natureza Jurídica & Precedente",
                                    subtitle = "Independência Uber/99 e jurisprudência TJSP",
                                    badge = "Legal",
                                    badgeColor = PetrolHeader,
                                    onClick = { showLegalNatureDialog = true }
                                )
                            }
                        }
                    }
                }

                GoOrNoScreen.DRIVER_SETUP -> {
                    item {
                        var driverName by remember(config.driverName) { mutableStateOf(config.driverName) }
                        var vehicleModel by remember(config.vehicleModel) { mutableStateOf(config.vehicleModel) }
                        var vehiclePlate by remember(config.vehiclePlate) { mutableStateOf(config.vehiclePlate) }
                        var rodizioAlertEnabled by remember(config.rodizioAlertEnabled) { mutableStateOf(config.rodizioAlertEnabled) }
                        var selectedStyle by remember(config.overlayDisplayStyle) {
                            mutableStateOf(OverlayDisplayStyle.fromName(config.overlayDisplayStyle))
                        }
                        var overlayTheme by remember(config.overlayTheme) {
                            mutableStateOf(config.overlayTheme)
                        }
                        var overlayFontSize by remember(config.overlayFontSize) {
                            mutableStateOf(config.overlayFontSize)
                        }
                        var overlayFontFamily by remember(config.overlayFontFamily) {
                            mutableStateOf(config.overlayFontFamily)
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
                                            "GREEN" -> Color(0xFF10E548)
                                            "YELLOW" -> Color(0xFFFFB800)
                                            else -> Color(0xFFFF3B30)
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
                                                OverlayDisplayStyle.COMPLETO -> {
                                                    val isColorido = overlayTheme == "COLORIDO"
                                                    val isClaro = overlayTheme == "CLARO" || overlayTheme == "LIGHT"
                                                    val isEscuro = !isColorido && !isClaro

                                                    val vColorVal = when (previewVerdictState) {
                                                        "GREEN" -> Color(0xFF16A34A)
                                                        "YELLOW" -> Color(0xFFEAB308)
                                                        else -> Color(0xFFDC2626)
                                                    }

                                                    val cardBg = when {
                                                        isColorido -> vColorVal
                                                        isClaro -> Color.White
                                                        else -> Color(0xFF1E2024)
                                                    }
                                                    val cardBorder = when {
                                                        isColorido -> when (previewVerdictState) {
                                                            "GREEN" -> Color(0xFF14532D) // Nitidamente mais escuro que a cor interna
                                                            "YELLOW" -> Color(0xFF78350F) // Ambar escuro
                                                            else -> Color(0xFF7F1D1D)   // Vinho escuro
                                                        }
                                                        else -> vColorVal
                                                    }
                                                    val primaryText = if (isClaro) Color(0xFF0F172A) else Color.White
                                                    val secondaryText = when {
                                                        isColorido -> Color(0xFFF1F5F9)
                                                        isClaro -> Color(0xFF64748B)
                                                        else -> Color(0xFF94A3B8)
                                                    }

                                                    val previewFontFamily = remember(overlayFontFamily) {
                                                        when (overlayFontFamily.uppercase(java.util.Locale.ROOT)) {
                                                            "PLUS_JAKARTA_SANS", "PLUS_JAKARTA", "JAKARTA" -> try {
                                                                FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Bold))
                                                            } catch (_: Exception) {
                                                                FontFamily.SansSerif
                                                            }
                                                            "SISTEMA" -> FontFamily.Default
                                                            "ROBOTO" -> FontFamily.SansSerif
                                                            else -> FontFamily.SansSerif
                                                        }
                                                    }
                                                    val previewPillarFontSize = when (overlayFontSize) {
                                                        "P" -> 19.sp
                                                        "G" -> 25.sp
                                                        "GG" -> 29.sp
                                                        else -> 22.sp
                                                    }

                                                    // Card com borda externa e lip inferior mais espesso (6dp)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .shadow(8.dp, RoundedCornerShape(18.dp))
                                                            .background(cardBorder, RoundedCornerShape(18.dp))
                                                            .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 6.dp)
                                                    ) {
                                                        Surface(
                                                            color = cardBg,
                                                            shape = RoundedCornerShape(14.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(modifier = Modifier.padding(14.dp, 10.dp, 14.dp, 10.dp)) {
                                                                // Linha Superior
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Surface(
                                                                            color = Color(0xFFFACC15),
                                                                            shape = RoundedCornerShape(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "99",
                                                                                color = Color.Black,
                                                                                fontWeight = FontWeight.Black,
                                                                                fontSize = 11.sp,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            )
                                                                        }
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text(
                                                                            text = "22 min • 12,4 km",
                                                                            color = primaryText,
                                                                            fontSize = 14.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontFamily = previewFontFamily
                                                                        )
                                                                    }
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Text("15s", color = secondaryText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("✕", color = secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.height(8.dp))

                                                                // 3 Pilares
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    val kmPreview = when (previewVerdictState) {
                                                                        "GREEN" -> "2,00"
                                                                        "YELLOW" -> "1,60"
                                                                        else -> "1,05"
                                                                    }
                                                                    val hrPreview = when (previewVerdictState) {
                                                                        "GREEN" -> "68"
                                                                        "YELLOW" -> "45"
                                                                        else -> "29"
                                                                    }
                                                                    val ratingPreview = when (previewVerdictState) {
                                                                        "GREEN" -> "4,92"
                                                                        "YELLOW" -> "4,75"
                                                                        else -> "4,20"
                                                                    }
                                                                    val kmBar = when (previewVerdictState) {
                                                                        "GREEN" -> Color(0xFF16A34A)
                                                                        "YELLOW" -> Color(0xFFCA8A04)
                                                                        else -> Color(0xFFDC2626)
                                                                    }
                                                                    val hrBar = when (previewVerdictState) {
                                                                        "GREEN" -> Color(0xFF16A34A)
                                                                        "YELLOW" -> Color(0xFFCA8A04)
                                                                        else -> Color(0xFFDC2626)
                                                                    }
                                                                    val ratingBar = when (previewVerdictState) {
                                                                        "GREEN" -> Color(0xFF16A34A)
                                                                        "YELLOW" -> Color(0xFFCA8A04)
                                                                        else -> Color(0xFFDC2626)
                                                                    }

                                                                    listOf(
                                                                        Triple("R$/Km", kmBar, kmPreview),
                                                                        Triple("R$/Hora", hrBar, hrPreview),
                                                                        Triple("Nota", ratingBar, ratingPreview)
                                                                    ).forEach { (lbl, barColor, valStr) ->
                                                                        Column(
                                                                            modifier = Modifier.weight(1f),
                                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                                        ) {
                                                                            Text(lbl, color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = previewFontFamily)
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                modifier = Modifier.padding(top = 2.dp)
                                                                            ) {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .size(3.5.dp, 18.dp)
                                                                                        .background(if (isColorido) Color.White else barColor, RoundedCornerShape(2.dp))
                                                                                )
                                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                                Text(
                                                                                    valStr,
                                                                                    color = primaryText,
                                                                                    fontSize = previewPillarFontSize,
                                                                                    fontWeight = FontWeight.Black,
                                                                                    fontFamily = previewFontFamily
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // ALERTA TOTALMENTE FORA DO CARD
                                                    Surface(
                                                        color = Color(0xFF7F1D1D),
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                        modifier = Modifier.padding(top = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚠️ Embarque (15 min)",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                OverlayDisplayStyle.SECUNDARIO -> {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalAlignment = Alignment.End
                                                    ) {
                                                        Surface(
                                                            color = vColor,
                                                            shape = RoundedCornerShape(16.dp),
                                                            shadowElevation = 6.dp
                                                        ) {
                                                            Text(
                                                                text = when (previewVerdictState) {
                                                                    "GREEN" -> "✓ ACEITAR"
                                                                    "YELLOW" -> "● ATENÇÃO"
                                                                    else -> "✕ RECUSAR"
                                                                },
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        listOf("2,00 /km", "68 /h", "4,92 nota").forEach { item ->
                                                            Surface(
                                                                color = Color(0xFF1E2024),
                                                                shape = RoundedCornerShape(14.dp),
                                                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                                                modifier = Modifier.padding(vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = item,
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 16.sp,
                                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

                                // Tema do Cartão
                                Text(
                                    text = "Tema do Cartão",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Escolha o acabamento visual do seu cartão flutuante:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Pair("COLORIDO", "🎨 Colorido"),
                                        Pair("ESCURO", "🌙 Escuro"),
                                        Pair("CLARO", "☀️ Claro")
                                    ).forEach { (themeKey, label) ->
                                        val isSel = overlayTheme == themeKey || (themeKey == "ESCURO" && overlayTheme == "DARK") || (themeKey == "CLARO" && overlayTheme == "LIGHT")
                                        Surface(
                                            color = if (isSel) PetrolHeader else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, if (isSel) PetrolHeader else CleanCardBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    overlayTheme = themeKey
                                                    savedFlag = false
                                                    val updated = config.copy(overlayTheme = themeKey)
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.applyConfig(updated)
                                                        notifyUser("Tema alterado: $label")
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSel) Color.White else PetrolDarkText,
                                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Tamanho dos Números (P / M / G)
                                Text(
                                    text = "Tamanho dos Números (Fonte)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PetrolDarkText
                                )
                                Text(
                                    text = "Ajuste a visibilidade das métricas para facilitar a leitura no trânsito",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Triple("P", "P (19sp)", "19sp"),
                                        Triple("M", "M (22sp)", "22sp"),
                                        Triple("G", "G (25sp)", "25sp"),
                                        Triple("GG", "GG (29sp)", "29sp")
                                    ).forEach { (sizeKey, label, _) ->
                                        val isSel = overlayFontSize == sizeKey
                                        Surface(
                                            color = if (isSel) PetrolHeader else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, if (isSel) PetrolHeader else CleanCardBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    overlayFontSize = sizeKey
                                                    savedFlag = false
                                                    val updated = config.copy(overlayFontSize = sizeKey)
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.applyConfig(updated)
                                                        notifyUser("Tamanho da fonte: $label")
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSel) Color.White else PetrolDarkText,
                                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Estilo da Fonte / Tipografia
                                Text(
                                    text = "Estilo da Fonte / Tipografia",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = PetrolDarkText
                                )
                                Text(
                                    text = "Escolha a tipografia ideal para visualização rápida no painel",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Pair("ROBOTO", "Roboto (Padrão)"),
                                        Pair("SISTEMA", "Sistema"),
                                        Pair("PLUS_JAKARTA_SANS", "Plus Jakarta Sans (Bold)")
                                    ).forEach { (fontKey, fontLabel) ->
                                        val isSel = overlayFontFamily == fontKey
                                        Surface(
                                            color = if (isSel) PetrolHeader else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, if (isSel) PetrolHeader else CleanCardBorder),
                                            modifier = Modifier.clickable {
                                                overlayFontFamily = fontKey
                                                savedFlag = false
                                                val updated = config.copy(overlayFontFamily = fontKey)
                                                scope.launch {
                                                    app.preferencesRepository.saveConfig(updated)
                                                    app.overlayPanel.applyConfig(updated)
                                                    notifyUser("Fonte: $fontLabel")
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = fontLabel,
                                                    color = if (isSel) Color.White else PetrolDarkText,
                                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = CleanCardBorder)

                                // Posição na Tela
                                Text(
                                    text = "Posição na Tela",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Onde o cartão aparece na tela durante chamadas:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Pair("LEFT", "Esquerda"),
                                        Pair("CENTER", "Centro"),
                                        Pair("RIGHT", "Direita")
                                    ).forEach { (posKey, posLabel) ->
                                        val isSel = horizontalPosition == posKey
                                        Surface(
                                            color = if (isSel) PetrolHeader else SoftSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, if (isSel) PetrolHeader else CleanCardBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    horizontalPosition = posKey
                                                    savedFlag = false
                                                    val updated = config.copy(overlayHorizontalPosition = posKey)
                                                    scope.launch {
                                                        app.preferencesRepository.saveConfig(updated)
                                                        app.overlayPanel.applyConfig(updated)
                                                        notifyUser("Posição alterada: $posLabel")
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = posLabel,
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

                                OutlinedTextField(
                                    value = vehiclePlate,
                                    onValueChange = { vehiclePlate = it.uppercase(); savedFlag = false },
                                    label = { Text("Placa do Veículo (ex: BRA2E19 ou ABC-1234)") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                val plateDigit = com.creativeam.goorno.engine.RodizioEvaluator.extractFinalPlateDigit(vehiclePlate)
                                val rodizioDay = if (plateDigit != null) com.creativeam.goorno.engine.RodizioEvaluator.getDayNameForDigit(plateDigit) else null
                                if (rodizioDay != null) {
                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🚗 Placa final $plateDigit • Rodízio SP às ${rodizioDay}s (07h-10h e 17h-20h)",
                                            color = Color(0xFF92400E),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = SoftSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Alerta de Rodízio SP (Centro Expandido)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = PetrolHeader)
                                            Text("Avisa se a origem ou destino da corrida estiver no anel de rodízio no seu horário restrito.", fontSize = 10.5.sp, color = MutedText)
                                        }
                                        Switch(
                                            checked = rodizioAlertEnabled,
                                            onCheckedChange = { rodizioAlertEnabled = it; savedFlag = false }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            driverName = driverName.ifBlank { "Motorista" },
                                            vehicleModel = vehicleModel.ifBlank { "Veículo" },
                                            vehiclePlate = vehiclePlate.trim().uppercase(),
                                            rodizioAlertEnabled = rodizioAlertEnabled,
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
                        // CARD DE EMBARQUE DISTANTE (CONFIGURÁVEL)
                        // =========================================================
                        var pickupDistThreshold by remember(config.distantPickupKmThreshold) {
                            mutableStateOf(config.distantPickupKmThreshold.toFloat())
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            text = "⚠️ EMBARQUE",
                                            color = LimeBright,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Limite de Embarque Distante",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = PetrolHeader
                                    )
                                }
                                Text(
                                    text = "Corridas com distância até o passageiro igual ou superior a este valor receberão o aviso de '⚠️ Emb. Longe':",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Distância máxima tolerada:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PetrolDarkText
                                    )
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", pickupDistThreshold),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = PetrolHeader,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = pickupDistThreshold,
                                    onValueChange = {
                                        val rounded = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 8.0f)
                                        pickupDistThreshold = rounded
                                        val updated = config.copy(distantPickupKmThreshold = rounded.toDouble())
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            app.overlayPanel.applyConfig(updated)
                                        }
                                    },
                                    valueRange = 0.5f..8.0f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PetrolHeader,
                                        activeTrackColor = TrafficYellow
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // =========================================================
                        // CARD DE CORRIDA CURTA (CONFIGURÁVEL)
                        // =========================================================
                        var shortTripLimitThreshold by remember(config.shortTripKmThreshold) {
                            mutableStateOf(config.shortTripKmThreshold.toFloat())
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CleanCardBorder),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            text = "⚡ CORRIDA CURTA",
                                            color = LimeBright,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Limite de Corrida Curta",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = PetrolHeader
                                    )
                                }
                                Text(
                                    text = "Corridas com distância total de até este valor (e com trânsito normal) receberão a abinha '⚡ CORRIDA CURTA' no card:",
                                    fontSize = 11.5.sp,
                                    color = MutedText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Distância máxima para corrida curta:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PetrolDarkText
                                    )
                                    Surface(
                                        color = SoftSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", shortTripLimitThreshold),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = PetrolHeader,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = shortTripLimitThreshold,
                                    onValueChange = {
                                        val rounded = (Math.round(it * 10f) / 10f).coerceIn(1.0f, 10.0f)
                                        shortTripLimitThreshold = rounded
                                        val updated = config.copy(shortTripKmThreshold = rounded.toDouble())
                                        scope.launch {
                                            app.preferencesRepository.saveConfig(updated)
                                            app.overlayPanel.applyConfig(updated)
                                        }
                                    },
                                    valueRange = 1.0f..10.0f,
                                    steps = 17,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PetrolHeader,
                                        activeTrackColor = LimeBright
                                    )
                                )
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
                                    onClick = { showAccessibilityDisclosureDialog = true },
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
                    val totalAnalyzedSum = filteredHistory.sumOf { it.offeredPrice }
                    val totalAnalyzedCount = filteredHistory.size
                    val avgPerRide = if (totalAnalyzedCount > 0) totalAnalyzedSum / totalAnalyzedCount else 0.0
                    val validHrOffers = filteredHistory.filter { (it.grossPerHour ?: 0.0) > 0.0 }
                    val avgPerHour = if (validHrOffers.isNotEmpty()) validHrOffers.mapNotNull { it.grossPerHour }.average() else 0.0
                    val validKmOffers = filteredHistory.filter { (it.grossPerKm ?: 0.0) > 0.0 }
                    val avgPerKm = if (validKmOffers.isNotEmpty()) validKmOffers.mapNotNull { it.grossPerKm }.average() else 0.0

                    val countTodas = realHistory.size
                    val countAceitas = realHistory.count { (it.actualReceivedPrice ?: 0.0) > 0.0 || it.verdict.contains("GREEN", ignoreCase = true) }
                    val countRecusadas = realHistory.count { (it.actualReceivedPrice == null || it.actualReceivedPrice == 0.0) && !it.verdict.contains("GREEN", ignoreCase = true) }
                    val countConfirmar = realHistory.count { it.actualReceivedPrice == null }

                    // 1. BARRA DE NAVEGAÇÃO SEMANAL (Estilo Referência Executiva)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Anterior",
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(20.dp).clickable { }
                                    )
                                    val nowCal = Calendar.getInstance()
                                    val weekLabel = SimpleDateFormat("d", Locale.forLanguageTag("pt-BR")).format(nowCal.time) + " — " +
                                            SimpleDateFormat("d MMM", Locale.forLanguageTag("pt-BR")).format(Date(nowCal.timeInMillis + 6 * 86400000L))
                                    Text(
                                        text = weekLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Próximo",
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(20.dp).clickable { }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val nowCal = Calendar.getInstance()
                                    for (dayIdx in 0..6) {
                                        val dayNum = ((nowCal.get(Calendar.DAY_OF_MONTH) + dayIdx - 1) % 30) + 1
                                        val isSelected = dayIdx == selectedDayIndex
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clickable { selectedDayIndex = dayIdx }
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp, 28.dp)
                                                        .background(Color(0xFF059669), RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp, 3.dp)
                                                        .background(Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "$dayNum",
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. FILTRO DE APLICATIVO [ Todos ] [ Uber ] [ 99 ]
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (histAppFilter == "TODOS") Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { histAppFilter = "TODOS" }
                            ) {
                                Text(
                                    text = "Todos",
                                    color = if (histAppFilter == "TODOS") Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                color = if (histAppFilter == "UBER") Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { histAppFilter = "UBER" }
                            ) {
                                Text(
                                    text = "Uber",
                                    color = if (histAppFilter == "UBER") Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                color = if (histAppFilter == "99") Color(0xFFFACC15) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { histAppFilter = "99" }
                            ) {
                                Text(
                                    text = "99",
                                    color = if (histAppFilter == "99") Color.Black else Color(0xFF64748B),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (realHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            app.database.rideOfferDao().clearAllRealOffers()
                                            notifyUser("Histórico local limpo.")
                                        }
                                    }
                                ) {
                                    Text("Limpar", color = TrafficRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. SEGMENTO DE STATUS: [ Todas 94 ] [ Aceitas 4 ] [ Recusadas 90 ] [ Confirmar 0 ]
                    item {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Triple("TODAS", "Todas", countTodas),
                                    Triple("ACEITAS", "Aceitas", countAceitas),
                                    Triple("RECUSADAS", "Recusadas", countRecusadas),
                                    Triple("CONFIRMAR", "Confirmar", countConfirmar)
                                ).forEach { (id, label, count) ->
                                    val isSel = histStatusFilter == id
                                    Surface(
                                        color = if (isSel) PureWhiteSurface else Color.Transparent,
                                        shape = RoundedCornerShape(9.dp),
                                        shadowElevation = if (isSel) 1.5.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { histStatusFilter = id }
                                    ) {
                                        Text(
                                            text = "$label $count",
                                            color = if (isSel) Color(0xFF0F172A) else Color(0xFF64748B),
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. CARD EXECUTIVO DE RESUMO (Total analisado)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Total analisado",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", totalAnalyzedSum),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "$totalAnalyzedCount Solicitações analisadas",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", avgPerRide),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Média por corrida",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", avgPerHour),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Por hora em corrida",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", avgPerKm),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Por km em corrida",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. BARRA DE BUSCA PELO VALOR
                    item {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = histSearchQuery,
                                    onValueChange = { histSearchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (histSearchQuery.isEmpty()) {
                                            Text(
                                                text = "Buscar corrida pelo valor (ex.: 23,50)",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (histSearchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpar",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp).clickable { histSearchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    // 6. CABEÇALHO DO DIA
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val todayStr = SimpleDateFormat("EEE, d MMM", Locale.forLanguageTag("pt-BR")).format(Date()).uppercase()
                            Text(
                                text = todayStr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", totalAnalyzedSum),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // 7. LISTA DE OFERTAS
                    if (filteredHistory.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (realHistory.isEmpty()) "Nenhuma oferta real gravada ainda. Quando tocar uma corrida na Uber ou 99, ela aparecerá aqui automaticamente." else "Nenhuma corrida corresponde ao filtro selecionado.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredHistory, key = { it.id }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                                if (valPaid != null) {
                                                    notifyUser("Corrida marcada como Aceita!")
                                                } else {
                                                    notifyUser("Corrida desmarcada!")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                GoOrNoScreen.SECURITY -> {
                    item {
                        // Seletor Superior de Abas (Áreas de Risco vs Regiões Favoritas - Estilo Executivo)
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    color = if (securitySelectedTab == 0) PureWhiteSurface else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp),
                                    shadowElevation = if (securitySelectedTab == 0) 1.5.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { securitySelectedTab = 0 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = if (securitySelectedTab == 0) Color(0xFFDC2626) else Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Áreas de Risco",
                                            fontWeight = if (securitySelectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (securitySelectedTab == 0) Color(0xFF0F172A) else Color(0xFF64748B)
                                        )
                                    }
                                }

                                Surface(
                                    color = if (securitySelectedTab == 1) PureWhiteSurface else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp),
                                    shadowElevation = if (securitySelectedTab == 1) 1.5.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { securitySelectedTab = 1 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (securitySelectedTab == 1) Color(0xFF059669) else Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Regiões Favoritas",
                                            fontWeight = if (securitySelectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (securitySelectedTab == 1) Color(0xFF0F172A) else Color(0xFF64748B)
                                        )
                                    }
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
                                        "• Sem Cliques Automáticos: O GoOrNo jamais clica, aceita ou recusa viagens por você. A decisão final é sempre sua.\n" +
                                        "• Privacidade de Capturas de Tela: O OCR processa a imagem em memória via ML Kit local e descarta o bitmap imediatamente, sem armazenar prints.\n" +
                                        "• Zero Credenciais: O GoOrNo nunca solicita senha ou login da sua conta Uber ou 99.\n" +
                                        "• Proteção de Dados (LGPD): Seus dados cadastrais (telefone, PIN e placa) são protegidos com hash criptográfico e usados apenas para manter seus cálculos e metas salvos.",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://goorno-app.vercel.app/terms")))
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Termos de Uso", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://goorno-app.vercel.app/privacy")))
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Privacidade LGPD", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showLegalNatureDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PetrolHeader),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    Text("⚖️ Ver Natureza Jurídica & Precedente TJSP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    color = Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Exclusão Definitiva de Conta e Dados (LGPD)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TrafficRed
                                        )
                                        Text(
                                            text = "Conforme o Art. 18 da LGPD e as diretrizes do Google Play, você pode solicitar a qualquer momento a exclusão imediata e irreversível da sua conta, licença e backups da nuvem.",
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                        Button(
                                            onClick = { showDeleteAccountConfirmDialog = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = TrafficRed,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Solicitar Exclusão de Conta e Dados", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
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

                // =====================================================
                // TELA 13: FALE CONOSCO / CAIXA POSTAL DIRETA
                // =====================================================
                GoOrNoScreen.CONTACT_SUPPORT -> {
                    item {
                        ContactSupportScreenCard(
                            onBackToSettings = { currentScreen = GoOrNoScreen.SETTINGS },
                            onNotify = notifyUser
                        )
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Uma nova versão com melhorias de velocidade, estabilidade e precisão foi disponibilizada para seu aplicativo.",
                        fontSize = 11.5.sp,
                        color = PetrolDarkText,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Toque no botão abaixo para baixar e atualizar. As novidades completas serão exibidas assim que o aplicativo abrir após a instalação.",
                        fontSize = 10.5.sp,
                        color = MutedText,
                        lineHeight = 14.5.sp
                    )
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
    // DIALOG PÓS-ATUALIZAÇÃO: O QUE HÁ DE NOVO NA VERSÃO
    // -------------------------------------------------------------
    if (showWhatsNewDialog) {
        val currentWhatsNewList = listOf(
            "Alerta de Área de Risco 100% informativo: a decisão é sua e a região de risco não força mais card vermelho.",
            "Distância de embarque respeita estritamente o seu limite configurado (ex: 2.0 km sem alertas precoces).",
            "Identificação ampliada de paradas em viagens da 99 e Uber.",
            "Trava anti-delay: bloqueia cards atrasados sobre o mapa vazio.",
            "Blindagem contra fechamento ao usar WhatsApp ou outros aplicativos."
        )

        AlertDialog(
            onDismissRequest = {
                showWhatsNewDialog = false
                versionPrefs.edit().putInt("last_seen_version_code", currentAppVersionCode).apply()
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SoftSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✨", fontSize = 18.sp)
                        }
                    }
                    Column {
                        Text(
                            text = "Novidades da Versão",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = PetrolDarkText
                        )
                        Text(
                            text = "GoOrNo v$currentAppVersionName (Build $currentAppVersionCode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TrafficGreen
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Seu aplicativo foi atualizado com sucesso! Confira as melhorias ativas:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PetrolDarkText
                    )
                    Surface(
                        color = SoftSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentWhatsNewList.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("✓", color = TrafficGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    Text(
                                        text = item,
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.5.sp,
                                        color = PetrolDarkText
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWhatsNewDialog = false
                        versionPrefs.edit().putInt("last_seen_version_code", currentAppVersionCode).apply()
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
                        text = "Entendido, vamos rodar! 🚀",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
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
    // DIALOG 2: CONFIRMAÇÃO DE CADASTRO DE ÁREA DE RISCO (RUA VS BAIRRO EDITÁVEL)
    // -------------------------------------------------------------
    if (pendingRiskAddressToConfirm != null) {
        val rawAddr = pendingRiskAddressToConfirm!!
        val candidate = remember(rawAddr) { com.creativeam.goorno.engine.RiskAreaEvaluator.extractStreetAndDistrict(rawAddr) }
        var targetZone by remember(candidate) { mutableStateOf(candidate.street) }

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
                        text = "Escolha se deseja cadastrar a Rua específica ou o Bairro, ou edite o texto abaixo:",
                        fontSize = 12.sp,
                        color = PetrolDarkText
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            onClick = { targetZone = candidate.street },
                            color = if (targetZone.equals(candidate.street, ignoreCase = true)) Color(0xFFFEE2E2) else SoftSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (targetZone.equals(candidate.street, ignoreCase = true)) TrafficRed else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "📍 Rua:\n${candidate.street}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (targetZone.equals(candidate.street, ignoreCase = true)) TrafficRed else PetrolDarkText,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }
                        if (candidate.district != null) {
                            Surface(
                                onClick = { targetZone = candidate.district },
                                color = if (targetZone.equals(candidate.district, ignoreCase = true)) Color(0xFFFEE2E2) else SoftSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (targetZone.equals(candidate.district, ignoreCase = true)) TrafficRed else Color.Transparent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🏘️ Bairro:\n${candidate.district}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (targetZone.equals(candidate.district, ignoreCase = true)) TrafficRed else PetrolDarkText,
                                    modifier = Modifier.padding(8.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = targetZone,
                        onValueChange = { targetZone = it },
                        label = { Text("Nome da Rua ou Região") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "Quando uma nova corrida for chamada para esta rua ou região, o copiloto exibirá um alerta vermelho em destaque na tela.",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTerm = targetZone.trim()
                        if (cleanTerm.length >= 3) {
                            val currentList = com.creativeam.goorno.engine.RiskAreaEvaluator.parseCustomZonesList(config.customRiskZones)
                            val updatedList = (currentList + cleanTerm).distinct()
                            scope.launch {
                                app.preferencesRepository.saveConfig(
                                    config.copy(customRiskZones = updatedList.joinToString(", "))
                                )
                                notifyUser("⚠️ '$cleanTerm' cadastrado como Área de Risco!")
                            }
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
        val rawAddr = pendingFavoriteAddressToConfirm!!
        val candidate = remember(rawAddr) { com.creativeam.goorno.engine.FavoriteAreaEvaluator.extractStreetAndDistrict(rawAddr) }
        var targetZone by remember(candidate) { mutableStateOf(candidate.street) }

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
                        text = "Cadastrar Local Favorito",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = PetrolDarkText
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Escolha se deseja cadastrar a Rua específica ou o Bairro, ou edite o texto abaixo:",
                        fontSize = 12.sp,
                        color = PetrolDarkText
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            onClick = { targetZone = candidate.street },
                            color = if (targetZone.equals(candidate.street, ignoreCase = true)) Color(0xFFECFDF5) else SoftSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (targetZone.equals(candidate.street, ignoreCase = true)) Color(0xFF059669) else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "📍 Rua:\n${candidate.street}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (targetZone.equals(candidate.street, ignoreCase = true)) Color(0xFF059669) else PetrolDarkText,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }
                        if (candidate.district != null) {
                            Surface(
                                onClick = { targetZone = candidate.district },
                                color = if (targetZone.equals(candidate.district, ignoreCase = true)) Color(0xFFECFDF5) else SoftSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (targetZone.equals(candidate.district, ignoreCase = true)) Color(0xFF059669) else Color.Transparent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🏘️ Bairro:\n${candidate.district}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (targetZone.equals(candidate.district, ignoreCase = true)) Color(0xFF059669) else PetrolDarkText,
                                    modifier = Modifier.padding(8.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = targetZone,
                        onValueChange = { targetZone = it },
                        label = { Text("Nome da Rua ou Região") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "Quando uma nova corrida for chamada para esta região ou com embarque nela, o card flutuante exibirá um destaque verde com estrela.",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTerm = targetZone.trim()
                        if (cleanTerm.length >= 3) {
                            val currentList = com.creativeam.goorno.engine.FavoriteAreaEvaluator.parseCustomZonesList(config.customFavoriteZones)
                            val updatedList = (currentList + cleanTerm).distinct()
                            scope.launch {
                                app.preferencesRepository.saveConfig(
                                    config.copy(customFavoriteZones = updatedList.joinToString(", "))
                                )
                                notifyUser("⭐ '$cleanTerm' cadastrado como Local Favorito!")
                            }
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
                    Text("Sim, Cadastrar Local Favorito", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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

    if (showAccessibilityDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosureDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚖️", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "Transparência & Acessibilidade",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = PetrolDarkText
                        )
                        Text(
                            text = "Declaração de Uso da API de Acessibilidade",
                            fontSize = 10.5.sp,
                            color = MutedText
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Para calcular a rentabilidade enquanto você dirige com segurança, o GoOrNo precisa da permissão de Acessibilidade:",
                        fontSize = 12.sp,
                        color = PetrolDarkText,
                        lineHeight = 16.sp
                    )
                    Surface(
                        color = SoftSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓ Leitura exclusiva de valor, distância e destino das ofertas da Uber e 99.", fontSize = 11.sp, color = TrafficGreen, fontWeight = FontWeight.Bold)
                            Text("✓ Exibição do card flutuante com cálculo de R$/km e R$/hora.", fontSize = 11.sp, color = PetrolDarkText)
                            Text("✗ NÃO lê mensagens, conversas, fotos, senhas ou histórico.", fontSize = 11.sp, color = TrafficRed, fontWeight = FontWeight.Bold)
                            Text("✗ NÃO clica, não aceita e não recusa corridas por você (zero automação).", fontSize = 11.sp, color = TrafficRed, fontWeight = FontWeight.Bold)
                            Text("✗ NÃO coleta credenciais ou login das suas contas de motorista.", fontSize = 11.sp, color = PetrolDarkText)
                        }
                    }
                    Text(
                        text = "Ao tocar em 'Continuar', você será levado às configurações de Acessibilidade do Android para habilitar o serviço 'GoOrNo'.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDisclosureDialog = false
                        onRequestAccessibilityPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continuar para Configurações", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAccessibilityDisclosureDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Agora não", color = MutedText, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                }
            }
        )
    }

    if (showLegalNatureDialog) {
        AlertDialog(
            onDismissRequest = { showLegalNatureDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚖️", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "Natureza Jurídica & Independência",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = PetrolDarkText
                        )
                        Text(
                            text = "Precedentes e Autonomia do Motorista",
                            fontSize = 10.5.sp,
                            color = MutedText
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = SoftSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CleanCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1. Natureza do Serviço", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PetrolHeader)
                            Text("O GoOrNo é uma ferramenta independente de apoio à análise de rentabilidade de corridas para motoristas de aplicativos. Sua finalidade é auxiliar o motorista na avaliação econômica de uma oferta, utilizando métricas configuradas por ele e as informações necessárias à análise.", fontSize = 11.sp, color = PetrolDarkText, lineHeight = 15.sp)

                            Text("2. Funcionamento e Autonomia", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PetrolHeader)
                            Text("Mediante autorização de acesso concedida pelo usuário no aparelho, o GoOrNo interpreta informações da oferta para produzir uma classificação indicativa: compensa, merece análise ou não compensa.\n\nO resultado é uma estimativa baseada nas informações disponíveis e nas metas do motorista. Não representa garantia de lucro ou recomendação infalível.\n\nO GoOrNo não aceita nem recusa corridas automaticamente (zero autoclique). A decisão final e o toque na tela pertencem 100% ao motorista.", fontSize = 11.sp, color = PetrolDarkText, lineHeight = 15.sp)

                            Text("3. Independência de Plataformas (Uber e 99)", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PetrolHeader)
                            Text("O GoOrNo é independente e não possui parceria, patrocínio ou aprovação da Uber ou da 99. As referências a essas marcas destinam-se exclusivamente a indicar compatibilidade operacional (Art. 132, IV da Lei de Propriedade Industrial nº 9.279/1996).", fontSize = 11.sp, color = PetrolDarkText, lineHeight = 15.sp)

                            Text("4. Segurança e Proteção de Dados (LGPD)", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PetrolHeader)
                            Text("O GoOrNo não solicita senhas ou credenciais de login das contas Uber/99. A leitura da tela é efêmera em memória RAM. Os dados de cadastro (telefone, PIN e placa) são protegidos e usados apenas para manter seus backups.", fontSize = 11.sp, color = PetrolDarkText, lineHeight = 15.sp)

                            Text("5. Precedente Jurídico do TJSP", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PetrolHeader)
                            Text("A decisão do Tribunal de Justiça de São Paulo no Agravo de Instrumento nº 2207360-65.2023.8.26.0000 constitui precedente relevante de mercado sobre a legitimidade de ferramentas auxiliares de leitura de tela para motoristas autônomos.", fontSize = 11.sp, color = PetrolDarkText, lineHeight = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLegalNatureDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entendi", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        )
    }

    if (showDeleteAccountConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) showDeleteAccountConfirmDialog = false
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = PureWhiteSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "Excluir Conta e Dados?",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = TrafficRed
                        )
                        Text(
                            text = "Direito de Eliminação - Art. 18 LGPD",
                            fontSize = 10.5.sp,
                            color = MutedText
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Esta ação é IRREVERSÍVEL. Todos os dados abaixo serão excluídos permanentemente:",
                        fontSize = 12.sp,
                        color = PetrolDarkText,
                        lineHeight = 16.sp
                    )
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Sua licença e vínculo de telefone/placa no servidor", fontSize = 11.sp, color = TrafficRed)
                            Text("• Seu backup de metas, custos e histórico na nuvem", fontSize = 11.sp, color = TrafficRed)
                            Text("• Suas preferências locais salvas neste celular", fontSize = 11.sp, color = TrafficRed)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        scope.launch {
                            val savedWhatsApp = com.creativeam.goorno.data.license.LicenseManager.getSavedWhatsApp(context)
                            val savedPin = com.creativeam.goorno.data.license.LicenseManager.getSavedPin(context)
                            val savedRecovery = com.creativeam.goorno.data.license.LicenseManager.getSavedRecovery(context)
                            val res = com.creativeam.goorno.data.license.LicenseManager.deleteAccountOnline(
                                context = context,
                                whatsapp = savedWhatsApp,
                                pin = savedPin,
                                recoverySecret = savedRecovery
                            )
                            isDeletingAccount = false
                            showDeleteAccountConfirmDialog = false
                            res.onSuccess {
                                app.preferencesRepository.saveConfig(DriverFinancialConfig())
                                com.creativeam.goorno.data.license.LicenseManager.saveWhatsApp(context, "")
                                com.creativeam.goorno.data.license.LicenseManager.savePin(context, "")
                                notifyUser("Conta e dados excluídos com sucesso.")
                            }.onFailure { err ->
                                notifyUser("Erro ao excluir conta: ${err.message}")
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = TrafficRed, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isDeletingAccount) "Excluindo..." else "Sim, Excluir Definitivamente",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmDialog = false },
                    enabled = !isDeletingAccount,
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
                    text = result.riskAlertBadge?.let { if (it.startsWith("⚠️")) it else "⚠️ $it" } ?: "⚠️ Área de risco",
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
    onUpdateReceivedPrice: (Double?) -> Unit
) {
    var showAuditInput by remember { mutableStateOf(false) }
    var showAcceptConfirmDialog by remember { mutableStateOf(false) }
    var showUnmarkConfirmDialog by remember { mutableStateOf(false) }
    var showRiskAddressChooser by remember { mutableStateOf(false) }
    var showFavAddressChooser by remember { mutableStateOf(false) }
    var paidText by remember { mutableStateOf(entity.actualReceivedPrice?.toString() ?: entity.offeredPrice.toString()) }
    val dateStr = remember(entity.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(entity.timestampMillis))
    }

    val riskCheck = remember(entity.originAddress, entity.destinationAddress, config) {
        RiskAreaEvaluator.evaluate(entity.originAddress, entity.destinationAddress, config)
    }

    val favCheck = remember(entity.originAddress, entity.destinationAddress, config) {
        FavoriteAreaEvaluator.evaluate(entity.originAddress, entity.destinationAddress, config)
    }

    val is99 = entity.sourceApp.contains("99", ignoreCase = true)

    val verdictColor = when {
        entity.verdict.contains("GREEN", ignoreCase = true) || entity.verdict.contains("Excelente", ignoreCase = true) -> Color(0xFF16A34A)
        entity.verdict.contains("YELLOW", ignoreCase = true) || entity.verdict.contains("Atenção", ignoreCase = true) -> Color(0xFFCA8A04)
        entity.verdict.contains("RED", ignoreCase = true) || entity.verdict.contains("Recusar", ignoreCase = true) -> Color(0xFFDC2626)
        else -> Color(0xFF64748B)
    }

    val verdictText = when {
        entity.verdict.contains("GREEN", ignoreCase = true) || entity.verdict.contains("Excelente", ignoreCase = true) -> "● Ótima"
        entity.verdict.contains("YELLOW", ignoreCase = true) || entity.verdict.contains("Atenção", ignoreCase = true) -> "● Atenção"
        entity.verdict.contains("RED", ignoreCase = true) || entity.verdict.contains("Recusar", ignoreCase = true) -> "● Ruim"
        else -> "● Neutro"
    }

    val totalKmStr = entity.totalDistanceKm?.let {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it)
    } ?: "-- km"
    val totalMinStr = entity.totalDurationMin?.let { "$it min" } ?: "-- min"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // LINHA 1: [BADGE APP] R$ VALOR · HORÁRIO                     ● VEREDITO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (is99) Color(0xFFFACC15) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (is99) "99" else "Uber",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = if (is99) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", entity.offeredPrice),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.5.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "· $dateStr",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = verdictText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = verdictColor
            )
        }

        // LINHA 2: DISTÂNCIA/TEMPO E BOTÃO "✓ ACEITEI / CONFIRMAR" COM DIÁLOGO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: $totalKmStr · $totalMinStr",
                fontSize = 12.5.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )

            Surface(
                color = if (entity.actualReceivedPrice != null) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (entity.actualReceivedPrice != null) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                modifier = Modifier.clickable {
                    if (entity.actualReceivedPrice == null) {
                        showAcceptConfirmDialog = true
                    } else {
                        showUnmarkConfirmDialog = true
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (entity.actualReceivedPrice != null) "✓ Aceitei" else "✓ Confirmar",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entity.actualReceivedPrice != null) Color(0xFF059669) else Color(0xFF334155)
                    )
                }
            }
        }

        // LINHA 3: 3 MÉTRICAS SÓBRIAS (R$/Km | R$/Hora | Nota limpa sem 'Boa')
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("R$/Km", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                    Text(
                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", entity.grossPerKm ?: 0.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }
                Box(modifier = Modifier.size(1.dp, 20.dp).background(Color(0xFFE2E8F0)))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("R$/Hora", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                    Text(
                        text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f", entity.grossPerHour ?: 0.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }
                Box(modifier = Modifier.size(1.dp, 20.dp).background(Color(0xFFE2E8F0)))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nota", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                    val rLine = entity.reasonsSummary.split("|")
                        .map { it.trim() }
                        .firstOrNull { it.contains("Passageiro Nota", ignoreCase = true) }
                    val rScore = rLine?.let { Regex("[\\d.,]+").find(it)?.value } ?: "5,00"
                    Text(
                        text = "★ $rScore",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }

        // ALERTA DE ÁREA DE RISCO (se houver)
        if (riskCheck.hasRisk) {
            Surface(
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (riskCheck.shortBadge.startsWith("⚠️")) riskCheck.shortBadge else "⚠️ " + riskCheck.shortBadge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // REGIÃO FAVORITA (se houver)
        if (favCheck.isFavorite) {
            Surface(
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (favCheck.shortBadge.startsWith("⭐")) favCheck.shortBadge else "⭐ " + favCheck.shortBadge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color(0xFF065F46),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // ORIGEM E DESTINO (PARADAS DA ROTA) - Tocar abre Street View 360° diretamente
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { entity.originAddress?.let(onOpenStreetView) },
                    verticalAlignment = Alignment.Top
                ) {
                    Text("●", color = Color(0xFF10B981), fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp, top = 1.dp))
                    Text(
                        text = entity.originAddress?.takeIf { it.isNotBlank() } ?: "Origem não informada",
                        fontSize = 12.sp,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { entity.destinationAddress?.let(onOpenStreetView) },
                    verticalAlignment = Alignment.Top
                ) {
                    Text("🏁", fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp, top = 1.dp))
                    Text(
                        text = entity.destinationAddress?.takeIf { it.isNotBlank() } ?: "Destino não informado",
                        fontSize = 12.sp,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // AÇÕES RÁPIDAS (+Risco, +Favorito, Editar) - Botão 360° Maps removido para alinhamento perfeito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val hasOrigin = !entity.originAddress.isNullOrBlank()
            val hasDest = !entity.destinationAddress.isNullOrBlank()

            if (hasOrigin || hasDest) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (hasOrigin && hasDest) {
                                showRiskAddressChooser = true
                            } else if (hasOrigin) {
                                onMarkAddressAsRisk(entity.originAddress!!)
                            } else {
                                onMarkAddressAsRisk(entity.destinationAddress!!)
                            }
                        }
                ) {
                    Text(
                        text = "⚠️ +Risco",
                        color = Color(0xFFDC2626),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }

                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (hasOrigin && hasDest) {
                                showFavAddressChooser = true
                            } else if (hasOrigin) {
                                onMarkAddressAsFavorite(entity.originAddress!!)
                            } else {
                                onMarkAddressAsFavorite(entity.destinationAddress!!)
                            }
                        }
                ) {
                    Text(
                        text = "⭐ +Favorito",
                        color = Color(0xFF059669),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }
            }

            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { showAuditInput = !showAuditInput }
            ) {
                Text(
                    text = "✏️ Editar",
                    color = Color(0xFF334155),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 7.dp)
                )
            }
        }

        // INPUT DE EDIÇÃO DE VALOR RECEBIDO
        if (showAuditInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it.replace(",", ".") },
                    label = { Text("Valor Real Recebido (R$)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (entity.actualReceivedPrice != null) {
                    TextButton(
                        onClick = {
                            onUpdateReceivedPrice(null)
                            showAuditInput = false
                        }
                    ) {
                        Text("Limpar", color = Color(0xFFDC2626), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // DIÁLOGO DE CONFIRMAÇÃO: "Você realmente aceitou esta corrida?"
    if (showAcceptConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAcceptConfirmDialog = false },
            title = {
                Text(
                    text = "Confirmar Viagem Aceita?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Você realmente aceitou esta corrida no valor de R$ " +
                            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", entity.offeredPrice) + "?",
                        fontSize = 13.5.sp,
                        color = Color(0xFF334155)
                    )
                    Text(
                        text = "Ela será registrada como Aceita no seu relatório de faturamento.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAcceptConfirmDialog = false
                        onUpdateReceivedPrice(entity.offeredPrice)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Sim, Aceitei", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptConfirmDialog = false }) {
                    Text("Cancelar", color = Color(0xFF64748B))
                }
            }
        )
    }

    // DIÁLOGO PARA DESMARCAR CORRIDA ACEITA
    if (showUnmarkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnmarkConfirmDialog = false },
            title = {
                Text(
                    text = "Desmarcar Corrida Aceita?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Deseja remover o status de aceita desta corrida? Ela voltará para a lista geral sem valor conferido.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnmarkConfirmDialog = false
                        onUpdateReceivedPrice(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Sim, Desmarcar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmarkConfirmDialog = false }) {
                    Text("Cancelar", color = Color(0xFF64748B))
                }
            }
        )
    }

    // SELETOR: ORIGEM OU DESTINO PARA +RISCO
    if (showRiskAddressChooser) {
        AlertDialog(
            onDismissRequest = { showRiskAddressChooser = false },
            title = {
                Text("Cadastrar Área de Risco", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Qual endereço desta corrida você deseja cadastrar como área de risco?",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    if (!entity.originAddress.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRiskAddressChooser = false
                                    onMarkAddressAsRisk(entity.originAddress!!)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("● Origem (Embarque)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                Text(entity.originAddress!!, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (!entity.destinationAddress.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRiskAddressChooser = false
                                    onMarkAddressAsRisk(entity.destinationAddress!!)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🏁 Destino (Desembarque)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                Text(entity.destinationAddress!!, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRiskAddressChooser = false }) {
                    Text("Cancelar", color = Color(0xFF64748B))
                }
            }
        )
    }

    // SELETOR: ORIGEM OU DESTINO PARA +FAVORITO
    if (showFavAddressChooser) {
        AlertDialog(
            onDismissRequest = { showFavAddressChooser = false },
            title = {
                Text("Cadastrar Região Favorita", fontWeight = FontWeight.Bold, color = Color(0xFF059669))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Qual endereço desta corrida você deseja cadastrar como favorito?",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    if (!entity.originAddress.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFavAddressChooser = false
                                    onMarkAddressAsFavorite(entity.originAddress!!)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("● Origem (Embarque)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                Text(entity.originAddress!!, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (!entity.destinationAddress.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFavAddressChooser = false
                                    onMarkAddressAsFavorite(entity.destinationAddress!!)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🏁 Destino (Desembarque)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                Text(entity.destinationAddress!!, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFavAddressChooser = false }) {
                    Text("Cancelar", color = Color(0xFF64748B))
                }
            }
        )
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


