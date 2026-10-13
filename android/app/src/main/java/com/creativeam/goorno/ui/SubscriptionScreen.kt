package com.creativeam.goorno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativeam.goorno.data.license.LicenseManager
import com.creativeam.goorno.data.license.LicenseState
import com.creativeam.goorno.data.license.PixChargeData
import com.creativeam.goorno.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SubscriptionScreenCard(
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var licenseState by remember { mutableStateOf(LicenseManager.getCachedLicenseState(context)) }
    var whatsappInput by remember { mutableStateOf(LicenseManager.getSavedWhatsApp(context)) }
    var selectedPlan by remember { mutableStateOf("mensal") } // "mensal" ou "vitalicio"
    var pixData by remember { mutableStateOf<PixChargeData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var voucherInput by remember { mutableStateOf("") }
    var isRedeemingVoucher by remember { mutableStateOf(false) }

    // Silently refresh status on open if whatsapp was previously saved
    LaunchedEffect(Unit) {
        val saved = LicenseManager.getSavedWhatsApp(context)
        if (saved.isNotBlank()) {
            scope.launch {
                val res = LicenseManager.checkOrActivateOnline(context, saved, "check")
                res.onSuccess { state ->
                    licenseState = state
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Status Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(
                1.5.dp,
                when {
                    licenseState.status == "blocked" -> TrafficRed
                    licenseState.fraudAlert -> TrafficRed
                    licenseState.status == "expired" -> TrafficRed
                    licenseState.status == "active" -> TrafficGreen
                    else -> PetrolHeader
                }
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                licenseState.status == "blocked" || licenseState.status == "expired" -> Icons.Default.Cancel
                                licenseState.status == "active" -> Icons.Default.CheckCircle
                                else -> Icons.Default.Verified
                            },
                            contentDescription = null,
                            tint = when {
                                licenseState.status == "blocked" || licenseState.status == "expired" -> TrafficRed
                                licenseState.status == "active" -> TrafficGreen
                                else -> PetrolHeader
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (licenseState.status) {
                                "blocked" -> "CONTA BLOQUEADA"
                                "active" -> if (licenseState.plano == "vitalicio" || licenseState.plano == "cortesia") "PRO VITALÍCIO" else "PRO MENSAL ATIVO"
                                "trial" -> "TESTE GRÁTIS ATIVO"
                                "expired" -> "LICENÇA EXPIRADA"
                                else -> "STATUS DA LICENÇA"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = PetrolHeader
                        )
                    }

                    Surface(
                        color = when (licenseState.status) {
                            "active" -> TrafficGreenSoft
                            "trial" -> TrafficYellowSoft
                            else -> TrafficRedSoft
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (licenseState.status) {
                                "active" -> "ATIVO"
                                "trial" -> "${licenseState.trialDaysRemaining}d restantes"
                                "blocked" -> "BLOQUEADO"
                                else -> "EXPIRADO"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = when (licenseState.status) {
                                "active" -> TrafficGreen
                                "trial" -> TrafficYellow
                                else -> TrafficRed
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = when {
                        licenseState.status == "blocked" -> "Esta conta foi suspensa por suspeita de fraude ou compartilhamento indevido."
                        licenseState.status == "active" && (licenseState.plano == "vitalicio" || licenseState.plano == "cortesia") ->
                            "Parabéns! Você tem acesso ilimitado e permanente ao Copiloto GoOrNo."
                        licenseState.status == "active" ->
                            "Seu plano Pro está ativo. Válido até ${licenseState.validoAte.take(10)}. Aproveite as corridas lucrativas!"
                        licenseState.status == "trial" ->
                            "Você está utilizando o período de teste de 7 dias grátis. Todas as funções do copiloto estão liberadas."
                        else ->
                            "Seu período de teste expirou. Ative o plano Pro via PIX para continuar rodando com o leitor e copiloto."
                    },
                    fontSize = 13.sp,
                    color = MutedText,
                    lineHeight = 18.sp
                )

                if (licenseState.deviceMismatch) {
                    Surface(
                        color = TrafficRedSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = TrafficRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WhatsApp vinculado a outro aparelho. Clique em 'Transferir para este Aparelho' abaixo.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TrafficRed
                            )
                        }
                    }
                }
            }
        }

        // 2. WhatsApp Identification Card
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
                    text = "Identificação do Motorista",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PetrolHeader
                )
                Text(
                    text = "Sua licença e pagamentos ficam vinculados ao seu WhatsApp para você nunca perder seu acesso.",
                    fontSize = 12.sp,
                    color = MutedText
                )

                OutlinedTextField(
                    value = whatsappInput,
                    onValueChange = { whatsappInput = it },
                    label = { Text("WhatsApp (DDD + 9 dígitos)") },
                    placeholder = { Text("Ex: 11999998888") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PetrolHeader) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (whatsappInput.length < 10) {
                                onNotify("Informe seu WhatsApp completo com DDD.")
                                return@Button
                            }
                            isLoading = true
                            scope.launch {
                                val res = LicenseManager.checkOrActivateOnline(context, whatsappInput, "check")
                                isLoading = false
                                res.onSuccess { state ->
                                    licenseState = state
                                    onNotify(state.message)
                                }.onFailure { err ->
                                    onNotify(err.message ?: "Erro ao validar.")
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Verificar / Ativar Teste", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (licenseState.deviceMismatch) {
                        Button(
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    val res = LicenseManager.checkOrActivateOnline(context, whatsappInput, "transfer")
                                    isLoading = false
                                    res.onSuccess { state ->
                                        licenseState = state
                                        onNotify(state.message)
                                    }.onFailure { err ->
                                        onNotify(err.message ?: "Erro ao transferir.")
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = TrafficRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Transferir Aparelho", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Plan Selection & PIX Generation
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
                    text = "Escolha seu Plano Pro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PetrolHeader
                )

                // Option 1: Monthly Plan
                Surface(
                    color = if (selectedPlan == "mensal") SoftSurfaceVariant else PureWhiteSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (selectedPlan == "mensal") 2.dp else 1.dp,
                        if (selectedPlan == "mensal") PetrolHeader else CleanCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = "mensal" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPlan == "mensal",
                                onClick = { selectedPlan = "mensal" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Plano Mensal Pro", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PetrolHeader)
                                Text("Acesso completo por 30 dias", fontSize = 11.5.sp, color = MutedText)
                            }
                        }
                        Text("R$ 9,90", fontWeight = FontWeight.Black, fontSize = 16.sp, color = TrafficGreen)
                    }
                }

                // Option 2: Lifetime Plan
                Surface(
                    color = if (selectedPlan == "vitalicio") SoftSurfaceVariant else PureWhiteSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (selectedPlan == "vitalicio") 2.dp else 1.dp,
                        if (selectedPlan == "vitalicio") PetrolHeader else CleanCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = "vitalicio" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPlan == "vitalicio",
                                onClick = { selectedPlan = "vitalicio" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Plano Vitalício", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PetrolHeader)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(color = LimeBright, shape = RoundedCornerShape(6.dp)) {
                                        Text("RECOMENDADO", fontSize = 9.sp, fontWeight = FontWeight.Black, color = PetrolDarkText, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text("Pague uma única vez para sempre", fontSize = 11.5.sp, color = MutedText)
                            }
                        }
                        Text("R$ 39,90", fontWeight = FontWeight.Black, fontSize = 16.sp, color = PetrolHeader)
                    }
                }

                // Button to Generate PIX
                Button(
                    onClick = {
                        if (whatsappInput.length < 10) {
                            onNotify("Informe seu WhatsApp antes de gerar o PIX.")
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            val res = LicenseManager.createPixCharge(whatsappInput, selectedPlan)
                            isLoading = false
                            res.onSuccess { data ->
                                pixData = data
                                onNotify("Código PIX gerado com sucesso!")
                            }.onFailure { err ->
                                onNotify("Erro ao gerar PIX: ${err.message}")
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = LimeBright, contentColor = PetrolDarkText),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPlan == "vitalicio") "Gerar PIX Vitalício (R$ 39,90)" else "Gerar PIX Mensal (R$ 9,90)",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 4. PIX Result Display (QR Code & Copia e Cola)
        if (pixData != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.5.dp, TrafficGreen),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pagamento PIX Seguro (Asaas)",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = PetrolHeader
                    )
                    Text(
                        text = "Valor: R$ ${String.format("%.2f", pixData!!.value).replace(".", ",")} • Vencimento: ${pixData!!.dueDate}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = TrafficGreen
                    )

                    // Render QR Code Image from Base64
                    if (pixData!!.qrCodeBase64.isNotBlank()) {
                        val bitmap = remember(pixData!!.qrCodeBase64) {
                            try {
                                val cleanB64 = pixData!!.qrCodeBase64.substringAfter("base64,")
                                val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code PIX",
                                modifier = Modifier
                                    .size(180.dp)
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Copia e Cola Button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("PIX Copia e Cola GoOrNo", pixData!!.copyPastePix)
                            clipboard.setPrimaryClip(clip)
                            onNotify("Chave PIX copiada! Cole no seu banco em 'Pix Copia e Cola'.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar Código PIX (Copia e Cola)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    // Verify Payment Button
                    OutlinedButton(
                        onClick = {
                            isVerifying = true
                            scope.launch {
                                val res = LicenseManager.verifyPaymentOnline(context, pixData!!.paymentId, whatsappInput)
                                isVerifying = false
                                res.onSuccess { confirmed ->
                                    if (confirmed) {
                                        val newState = LicenseManager.getCachedLicenseState(context)
                                        licenseState = newState
                                        onNotify("🎉 Pagamento confirmado com sucesso! Licença ativada.")
                                        pixData = null
                                    } else {
                                        onNotify("Pagamento ainda em processamento. Aguarde alguns segundos e tente novamente.")
                                    }
                                }.onFailure {
                                    onNotify("Não foi possível confirmar ainda. Tente em 30 segundos.")
                                }
                            }
                        },
                        enabled = !isVerifying,
                        border = BorderStroke(1.dp, TrafficGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TrafficGreen)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TrafficGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Já Paguei! Verificar Pagamento Agora", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TrafficGreen)
                        }
                    }
                }
            }
        }

        // 4b. Cupom / Voucher de Cortesia
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PetrolHeader,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tem um Cupom / Voucher de Cortesia?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = PetrolHeader
                    )
                }

                Text(
                    text = "Se você recebeu um código promocional de bônus, parceria ou cortesia, digite abaixo para ativar na sua conta:",
                    fontSize = 11.5.sp,
                    color = MutedText,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = voucherInput,
                        onValueChange = { voucherInput = it.uppercase() },
                        label = { Text("Código do Cupom") },
                        placeholder = { Text("Ex: BEMVINDO7") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (whatsappInput.isBlank()) {
                                onNotify("Informe seu WhatsApp no topo antes de resgatar o cupom.")
                                return@Button
                            }
                            if (voucherInput.isBlank()) {
                                onNotify("Digite o código do cupom.")
                                return@Button
                            }
                            isRedeemingVoucher = true
                            scope.launch {
                                val res = LicenseManager.redeemVoucherOnline(
                                    context = context,
                                    whatsapp = whatsappInput,
                                    voucherCode = voucherInput
                                )
                                isRedeemingVoucher = false
                                res.onSuccess { msg ->
                                    val newState = LicenseManager.getCachedLicenseState(context)
                                    licenseState = newState
                                    voucherInput = ""
                                    onNotify(msg)
                                }.onFailure { err ->
                                    onNotify(err.message ?: "Erro ao resgatar cupom.")
                                }
                            }
                        },
                        enabled = !isRedeemingVoucher && voucherInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PetrolHeader,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isRedeemingVoucher) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Resgatar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 5. Anti-Fraud & Single-Device Policy Card
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, CleanCardBorder),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = PetrolHeader, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Segurança de Uso Individual",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PetrolHeader
                    )
                }

                Text(
                    text = "• Cada licença é pessoal e vinculada a um único smartphone.\n" +
                        "• Se você trocar de aparelho, você pode transferir sua licença automaticamente até 2 vezes por mês.\n" +
                        "• Compartilhamento de conta com terceiros é proibido e bloqueado automaticamente pelo sistema anti-fraude.",
                    fontSize = 11.5.sp,
                    color = MutedText,
                    lineHeight = 16.sp
                )

                Text(
                    text = "ID do Aparelho: ${LicenseManager.getDeviceId(context)}",
                    fontSize = 10.sp,
                    color = MutedText.copy(alpha = 0.7f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
