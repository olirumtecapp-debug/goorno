package com.creativeam.goorno.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativeam.goorno.R
import com.creativeam.goorno.data.auth.DriverAuthUser
import com.creativeam.goorno.data.auth.GoogleAuthManager
import com.creativeam.goorno.data.cloud.CloudSyncManager
import com.creativeam.goorno.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DriverLoginScreen(
    onLoginCompleted: (DriverAuthUser) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            PetrolHeader,
            Color(0xFF0F3B36),
            Color(0xFF092421)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Logo Oficial GoOrNo com Fundo Branco Arredondado
                Image(
                    painter = painterResource(id = R.drawable.logo_goorno),
                    contentDescription = "Logo GoOrNo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "GoOrNo",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Seu Copiloto Inteligente para Uber e 99",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA7F3D0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Cards de Destaque das Funcionalidades
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FeatureHighlightRow(
                            icon = "🚦",
                            title = "Semáforo em Tempo Real",
                            desc = "Calcula R$/km e R$/hora antes de você aceitar a corrida."
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        FeatureHighlightRow(
                            icon = "🛡️",
                            title = "Alerta de Áreas de Risco",
                            desc = "Avisos visuais na tela para você dirigir com segurança."
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        FeatureHighlightRow(
                            icon = "📹",
                            title = "Câmera Secreta Integrada",
                            desc = "Grava em segundo plano sem travar o celular nem gastar internet."
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        FeatureHighlightRow(
                            icon = "☁️",
                            title = "Backup na Nuvem Gratuito",
                            desc = "Trocou de celular? Suas metas e custos voltam na hora."
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rodapé com Botão Oficial do Google
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            isLoading = true
                            scope.launch {
                                val res = GoogleAuthManager.signInWithGoogle(activity)
                                isLoading = false
                                res.onSuccess { driverUser ->
                                    onNotify("Bem-vindo, ${driverUser.displayName}!")
                                    // Sincroniza / Restaura dados em segundo plano
                                    scope.launch {
                                        try {
                                            val app = context.applicationContext as? com.creativeam.goorno.GoOrNoApplication
                                            if (app != null) {
                                                val cfg = app.preferencesRepository.getConfigOnce()
                                                val offers = app.database.rideOfferDao().getAllRealOffersOnce()
                                                // Se já tiver dados locais, salva na nuvem Google
                                                if (cfg.customRiskZones.isNotBlank() || cfg.customFavoriteZones.isNotBlank()) {
                                                    CloudSyncManager.autoSyncSettings(context, cfg, offers)
                                                } else {
                                                    // Se for celular recém instalado, restaura da nuvem
                                                    val restoreRes = CloudSyncManager.autoRestoreSettings(context)
                                                    restoreRes.onSuccess { (cloudCfg, cloudOffers) ->
                                                        app.preferencesRepository.saveConfig(cloudCfg)
                                                        if (cloudOffers.isNotEmpty()) {
                                                            app.database.rideOfferDao().insertAll(cloudOffers)
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                    onLoginCompleted(driverUser)
                                }.onFailure { err ->
                                    if (err.message != "Login cancelado.") {
                                        onNotify(err.message ?: "Falha ao autenticar com Google.")
                                    }
                                }
                            }
                        } else {
                            onNotify("Não foi possível iniciar o login.")
                        }
                    },
                    enabled = !isLoading,
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = PetrolHeader,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Conectando...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PetrolHeader
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Transparent,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continuar com o Google",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3C4043)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        val guestUser = DriverAuthUser(
                            uid = "offline_driver",
                            displayName = "Motorista",
                            isSignedIn = true
                        )
                        onLoginCompleted(guestUser)
                    }
                ) {
                    Text(
                        text = "Continuar sem login por enquanto",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA7F3D0)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "🔒 100% Gratuito e Seguro · Sem senhas ou códigos por SMS\nO GoOrNo nunca pede dados das suas contas de motorista.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 15.sp
            )
        }
    }
}
