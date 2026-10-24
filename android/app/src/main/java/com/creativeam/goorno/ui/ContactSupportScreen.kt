package com.creativeam.goorno.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativeam.goorno.data.license.LicenseManager
import com.creativeam.goorno.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ContactSupportScreenCard(
    onBackToSettings: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf(LicenseManager.getSavedWhatsApp(context)) }
    var contactEmail by remember { mutableStateOf("") }
    var preferredReply by remember { mutableStateOf("whatsapp") }
    var selectedCategory by remember { mutableStateOf("duvida") }
    var contactSubject by remember { mutableStateOf("") }
    var contactMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var successTicket by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            // CABEÇALHO DA CAIXA POSTAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = PetrolHeader,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = LimeBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Fale Conosco",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = PetrolHeader
                            )
                            Surface(
                                color = LimeSoftBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "CAIXA POSTAL",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    color = PetrolDarkText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Canal direto com o desenvolvedor Murilo Ferreira / CreativeAM",
                            fontSize = 11.5.sp,
                            color = MutedText
                        )
                    }
                }
            }

            HorizontalDivider(color = CleanCardBorder)

            if (successTicket != null) {
                // TELA DE SUCESSO
                Surface(
                    color = TrafficGreenSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TrafficGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = TrafficGreen,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "Mensagem Entregue com Sucesso!",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.5.sp,
                            color = PetrolHeader,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Sua solicitação foi registrada na Caixa Postal com o protocolo #${successTicket}.\nO desenvolvedor analisa cada mensagem recebida e responderá diretamente pelo seu canal escolhido.",
                            fontSize = 12.sp,
                            color = PetrolDarkText,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                successTicket = null
                                contactMessage = ""
                                onBackToSettings()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Concluir e Voltar aos Ajustes",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                if (errorMessage != null) {
                    Surface(
                        color = TrafficRedSoft,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TrafficRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = TrafficRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = TrafficRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Campo: Nome
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SEU NOME (OPCIONAL)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        placeholder = { Text("Ex: Carlos Motorista", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetrolHeader,
                            unfocusedBorderColor = CleanCardBorder
                        )
                    )
                }

                // Campo: WhatsApp / Telefone
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "WHATSAPP / TELEFONE (OPCIONAL)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        placeholder = { Text("DDD + Número (ex: 11999998888)", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetrolHeader,
                            unfocusedBorderColor = CleanCardBorder
                        )
                    )
                }

                // Campo: E-mail
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "E-MAIL (OPCIONAL)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        placeholder = { Text("seuemail@exemplo.com", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetrolHeader,
                            unfocusedBorderColor = CleanCardBorder
                        )
                    )
                }

                // Campo: Preferência de Retorno
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PREFERE RETORNO POR:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (preferredReply == "whatsapp") PetrolHeader else SoftSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (preferredReply == "whatsapp") PetrolHeader else CleanCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { preferredReply = "whatsapp" }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💬 WhatsApp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = if (preferredReply == "whatsapp") LimeBright else PetrolDarkText
                                )
                            }
                        }

                        Surface(
                            color = if (preferredReply == "email") PetrolHeader else SoftSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (preferredReply == "email") PetrolHeader else CleanCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { preferredReply = "email" }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✉️ E-mail",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = if (preferredReply == "email") LimeBright else PetrolDarkText
                                )
                            }
                        }
                    }
                }

                // Campo: Motivo do Contato
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MOTIVO DO CONTATO",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    val categories = listOf(
                        "duvida" to "❓ Dúvida",
                        "sugestao" to "💡 Sugestão",
                        "problema" to "⚠️ Problema",
                        "elogio" to "👏 Elogio",
                        "outro" to "📌 Outro"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (catId, catLabel) ->
                            val isSelected = selectedCategory == catId
                            Surface(
                                color = if (isSelected) LimeBright else SoftSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) LimeBright else CleanCardBorder),
                                modifier = Modifier.clickable { selectedCategory = catId }
                            ) {
                                Text(
                                    text = catLabel,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = if (isSelected) PetrolDarkText else MutedText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Campo: Assunto
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ASSUNTO",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = contactSubject,
                        onValueChange = { contactSubject = it },
                        placeholder = { Text("Ex: Sugestão para o leitor de chamadas da 99", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetrolHeader,
                            unfocusedBorderColor = CleanCardBorder
                        )
                    )
                }

                // Campo: Mensagem
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MENSAGEM *",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MutedText
                        )
                        Text(
                            text = "${contactMessage.length}/1000",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                    OutlinedTextField(
                        value = contactMessage,
                        onValueChange = { if (it.length <= 1000) contactMessage = it },
                        placeholder = { Text("Conte com detalhes o que você precisa ou deixe sua sugestão...", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetrolHeader,
                            unfocusedBorderColor = CleanCardBorder
                        )
                    )
                }

                // Botão Enviar Mensagem
                Button(
                    onClick = {
                        if (contactMessage.isBlank()) {
                            errorMessage = "Por favor, escreva sua mensagem antes de enviar."
                            return@Button
                        }
                        isSending = true
                        errorMessage = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val url = URL("https://goorno-app.vercel.app/api/contact")
                                val conn = (url.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    connectTimeout = 8000
                                    readTimeout = 8000
                                    doOutput = true
                                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                                    setRequestProperty("Accept", "application/json")
                                }

                                val payload = JSONObject().apply {
                                    put("name", contactName.trim().ifEmpty { "Motorista Autônomo" })
                                    put("contact", contactPhone.trim())
                                    put("email", contactEmail.trim())
                                    put("preferredReply", preferredReply)
                                    put("category", selectedCategory)
                                    put("subject", contactSubject.trim().ifEmpty { "Contato pelo App GoOrNo" })
                                    put("message", contactMessage.trim())
                                    put("source", "app")
                                }

                                conn.outputStream.use { os ->
                                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                                    os.flush()
                                }

                                val responseCode = conn.responseCode
                                if (responseCode in 200..299) {
                                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                                    val resJson = JSONObject(body)
                                    val ticket = resJson.optString("ticketId", "TKT-${System.currentTimeMillis().toString().takeLast(6)}")
                                    withContext(Dispatchers.Main) {
                                        isSending = false
                                        successTicket = ticket
                                        onNotify("Mensagem enviada com sucesso! Protocolo #$ticket")
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isSending = false
                                        errorMessage = "Servidor respondeu com código $responseCode. Tente novamente."
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isSending = false
                                    errorMessage = "Falha de conexão: ${e.localizedMessage ?: "Verifique sua internet"}"
                                }
                            }
                        }
                    },
                    enabled = !isSending && contactMessage.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PetrolHeader,
                        disabledContainerColor = PetrolHeader.copy(alpha = 0.5f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = LimeBright,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enviando Mensagem...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = LimeBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enviar Mensagem para a Caixa Postal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Opção direta de E-mail
                Surface(
                    color = SoftSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:contatocreativeam@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "Contato GoOrNo - Suporte")
                                    }
                                    context.startActivity(emailIntent)
                                } catch (_: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📧 Ou envie um e-mail direto para contatocreativeam@gmail.com",
                            fontSize = 11.sp,
                            color = PetrolDarkText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
