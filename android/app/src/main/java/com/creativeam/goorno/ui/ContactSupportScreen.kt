package com.creativeam.goorno.ui

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriverMailboxItem(
    val id: string,
    val subject: String,
    val message: String,
    val categoryLabel: String,
    val createdAt: String,
    val status: String,
    val adminReply: String?,
    val repliedAt: String?
)

@Composable
fun ContactSupportScreenCard(
    onBackToSettings: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf<String>("nova") } // "nova" ou "mensagens"

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf(LicenseManager.getSavedWhatsApp(context)) }
    var contactEmail by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("duvida") }
    var contactSubject by remember { mutableStateOf("") }
    var contactMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var successTicket by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Lista de mensagens do motorista
    var driverMessages by remember { mutableStateOf<List<DriverMailboxItem>>(emptyList()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var loadMessagesError by remember { mutableStateOf<String?>(null) }

    fun loadDriverMessages() {
        val phone = contactPhone.trim().ifEmpty { LicenseManager.getSavedWhatsApp(context) }
        if (phone.isBlank()) return
        isLoadingMessages = true
        loadMessagesError = null
        scope.launch(Dispatchers.IO) {
            try {
                val encodedPhone = URLEncoder.encode(phone, "UTF-8")
                val url = URL("https://goorno-app.vercel.app/api/contact/driver?phone=$encodedPhone")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("messages") ?: JSONArray()
                    val list = mutableListOf<DriverMailboxItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            DriverMailboxItem(
                                id = obj.optString("id"),
                                subject = obj.optString("subject", "Sem assunto"),
                                message = obj.optString("message", ""),
                                categoryLabel = obj.optString("categoryLabel", "Geral"),
                                createdAt = obj.optString("createdAt", ""),
                                status = obj.optString("status", "nova"),
                                adminReply = if (obj.has("adminReply") && !obj.isNull("adminReply")) obj.optString("adminReply") else null,
                                repliedAt = if (obj.has("repliedAt") && !obj.isNull("repliedAt")) obj.optString("repliedAt") else null
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        driverMessages = list
                        isLoadingMessages = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoadingMessages = false
                        loadMessagesError = "Código ${conn.responseCode} ao buscar mensagens."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingMessages = false
                    loadMessagesError = "Falha ao conectar: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDriverMessages()
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
                                text = "Caixa Postal GoOrNo",
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
                                    text = "CANAL DIRETO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    color = PetrolDarkText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Mensagens e respostas concentradas 100% no seu aplicativo",
                            fontSize = 11.5.sp,
                            color = MutedText
                        )
                    }
                }
            }

            // ABAS: [NOVA MENSAGEM] E [MINHAS MENSAGENS]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (activeTab == "nova") PetrolHeader else SoftSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (activeTab == "nova") PetrolHeader else CleanCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = "nova" }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (activeTab == "nova") LimeBright else MutedText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Escrever Mensagem",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeTab == "nova") Color.White else PetrolDarkText
                        )
                    }
                }

                val repliedCount = driverMessages.count { it.status == "respondida" && !it.adminReply.isNullOrBlank() }
                Surface(
                    color = if (activeTab == "mensagens") PetrolHeader else SoftSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (activeTab == "mensagens") PetrolHeader else CleanCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            activeTab = "mensagens"
                            loadDriverMessages()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = null,
                            tint = if (activeTab == "mensagens") LimeBright else MutedText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (driverMessages.isNotEmpty()) "Caixa de Entrada (${driverMessages.size})" else "Caixa de Entrada",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeTab == "mensagens") Color.White else PetrolDarkText
                        )
                        if (repliedCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = TrafficGreen,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                    }
                }
            }

            HorizontalDivider(color = CleanCardBorder)

            // ==========================================
            // ABA 1: FORMULÁRIO DE NOVA MENSAGEM
            // ==========================================
            if (activeTab == "nova") {
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
                                text = "Mensagem Entregue na Caixa Postal!",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.5.sp,
                                color = PetrolHeader,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Sua solicitação foi registrada com o protocolo #$successTicket.\nA resposta do desenvolvedor será entregue diretamente aqui na Caixa Postal do aplicativo.",
                                fontSize = 12.sp,
                                color = PetrolDarkText,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        activeTab = "mensagens"
                                        loadDriverMessages()
                                        successTicket = null
                                        contactMessage = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PetrolHeader),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Ver na Caixa de Entrada",
                                        color = LimeBright,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        successTicket = null
                                        contactMessage = ""
                                        onBackToSettings()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Voltar aos Ajustes",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
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

                    // AVISO DE RESPOSTA CONCENTRADA NO APP
                    Surface(
                        color = LimeSoftBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "📬", fontSize = 16.sp)
                            Text(
                                text = "A resposta do desenvolvedor será entregue aqui na Caixa Postal do seu app. Seus dados abaixo servem para localizar sua conta com precisão.",
                                fontSize = 11.sp,
                                color = PetrolDarkText,
                                lineHeight = 15.sp
                            )
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

                    // Linha: Telefone e Email
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "WHATSAPP / CELULAR *",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = MutedText
                            )
                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it },
                                placeholder = { Text("DDD + 9 dígitos", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
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

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "E-MAIL (OPCIONAL)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = MutedText
                            )
                            OutlinedTextField(
                                value = contactEmail,
                                onValueChange = { contactEmail = it },
                                placeholder = { Text("seu@email.com", fontSize = 13.sp, color = MutedText.copy(alpha = 0.6f)) },
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
                                        put("preferredReply", "app")
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
                                            loadDriverMessages()
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
                }
            } else {
                // ==========================================
                // ABA 2: CAIXA DE ENTRADA (MENSAGENS & RESPOSTAS)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Histórico de Mensagens & Respostas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PetrolHeader
                        )
                        IconButton(
                            onClick = { loadDriverMessages() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar",
                                tint = PetrolHeader,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isLoadingMessages) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = PetrolHeader,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else if (driverMessages.isEmpty()) {
                        Surface(
                            color = SoftSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "📭", fontSize = 28.sp)
                                Text(
                                    text = "Nenhuma mensagem encontrada",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PetrolHeader
                                )
                                Text(
                                    text = "Quando você enviar uma dúvida ou sugestão, ela aparecerá aqui com as respostas do desenvolvedor.",
                                    fontSize = 11.sp,
                                    color = MutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        driverMessages.forEach { item ->
                            val isReplied = item.status == "respondida" && !item.adminReply.isNullOrBlank()
                            Surface(
                                color = if (isReplied) TrafficGreenSoft else SoftSurfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (isReplied) TrafficGreen.copy(alpha = 0.4f) else CleanCardBorder),
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
                                            text = item.subject,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.5.sp,
                                            color = PetrolHeader
                                        )
                                        Surface(
                                            color = if (isReplied) TrafficGreen else TrafficAmber,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isReplied) "RESPONDIDA" else "AGUARDANDO",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.message,
                                        fontSize = 11.5.sp,
                                        color = PetrolDarkText,
                                        lineHeight = 16.sp
                                    )

                                    if (isReplied && !item.adminReply.isNullOrBlank()) {
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, TrafficGreen.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = TrafficGreen,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Resposta do Desenvolvedor Murilo / GoOrNo:",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.5.sp,
                                                        color = TrafficGreen
                                                    )
                                                }
                                                Text(
                                                    text = item.adminReply,
                                                    fontSize = 12.sp,
                                                    color = PetrolHeader,
                                                    fontWeight = FontWeight.Medium,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
