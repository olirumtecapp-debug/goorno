package com.creativeam.goorno.data.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class LicenseState(
    val allowed: Boolean = true,
    val status: String = "trial", // "trial", "active", "expired", "blocked"
    val plano: String = "trial", // "trial", "mensal", "vitalicio", "cortesia"
    val trialDaysRemaining: Int = 7,
    val validoAte: String = "",
    val message: String = "",
    val deviceMismatch: Boolean = false,
    val fraudAlert: Boolean = false,
    val whatsapp: String = ""
)

data class PixChargeData(
    val paymentId: String,
    val value: Double,
    val dueDate: String,
    val qrCodeBase64: String,
    val copyPastePix: String
)

object LicenseManager {

    private const val PREFS_NAME = "goorno_license_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_WHATSAPP = "saved_whatsapp"
    private const val KEY_STATUS = "cached_status"
    private const val KEY_PLANO = "cached_plano"
    private const val KEY_TRIAL_DAYS = "cached_trial_days"
    private const val KEY_VALID_UNTIL = "cached_valid_until"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"

    private const val API_BASE_URL = "https://goorno-app.vercel.app/api"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }
            id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "aid_$androidId"
            } else {
                "uuid_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getSavedWhatsApp(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WHATSAPP, "") ?: ""
    }

    fun saveWhatsApp(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WHATSAPP, cleanPhone(phone)).apply()
    }

    fun cleanPhone(phone: String): String {
        var digits = phone.replace(Regex("\\D"), "")
        if (digits.startsWith("55") && (digits.length == 12 || digits.length == 13)) {
            digits = digits.substring(2)
        }
        return digits
    }

    fun getCachedLicenseState(context: Context): LicenseState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val status = prefs.getString(KEY_STATUS, "trial") ?: "trial"
        val plano = prefs.getString(KEY_PLANO, "trial") ?: "trial"
        val days = prefs.getInt(KEY_TRIAL_DAYS, 7)
        val validUntil = prefs.getString(KEY_VALID_UNTIL, "") ?: ""
        val whatsapp = prefs.getString(KEY_WHATSAPP, "") ?: ""

        val allowed = status == "active" || (status == "trial" && days > 0)
        return LicenseState(
            allowed = allowed,
            status = status,
            plano = plano,
            trialDaysRemaining = days,
            validoAte = validUntil,
            message = if (allowed) "Licença Ativa" else "Licença Expirada",
            whatsapp = whatsapp
        )
    }

    suspend fun checkOrActivateOnline(
        context: Context,
        whatsappInput: String,
        action: String = "check"
    ): Result<LicenseState> = withContext(Dispatchers.IO) {
        val phone = cleanPhone(whatsappInput)
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Informe o WhatsApp com DDD e pelo menos 10 dígitos."))
        }

        saveWhatsApp(context, phone)
        val deviceId = getDeviceId(context)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

        try {
            val url = URL("$API_BASE_URL/license/check-or-activate")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val reqBody = JSONObject().apply {
                put("whatsapp", phone)
                put("deviceId", deviceId)
                put("deviceModel", deviceModel)
                put("action", action)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val respText = responseStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            val json = JSONObject(respText)
            val allowed = json.optBoolean("allowed", false)
            val status = json.optString("status", if (allowed) "active" else "expired")
            val plano = json.optString("plano", "trial")
            val daysLeft = json.optInt("trialDaysRemaining", 0)
            val validUntil = json.optString("validoAte", "")
            val message = json.optString("message", "")
            val deviceMismatch = json.optBoolean("deviceMismatch", false)
            val fraudAlert = json.optBoolean("fraudAlert", false)

            // Cache state
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_STATUS, status)
                .putString(KEY_PLANO, plano)
                .putInt(KEY_TRIAL_DAYS, daysLeft)
                .putString(KEY_VALID_UNTIL, validUntil)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()

            val state = LicenseState(
                allowed = allowed,
                status = status,
                plano = plano,
                trialDaysRemaining = daysLeft,
                validoAte = validUntil,
                message = message,
                deviceMismatch = deviceMismatch,
                fraudAlert = fraudAlert,
                whatsapp = phone
            )

            Result.success(state)
        } catch (e: Exception) {
            // Em caso de falha de conexão, usa cache se existir
            val cached = getCachedLicenseState(context)
            Result.success(cached.copy(message = "Offline: usando status salvo no aparelho."))
        }
    }

    suspend fun createPixCharge(
        whatsappInput: String,
        plan: String = "mensal"
    ): Result<PixChargeData> = withContext(Dispatchers.IO) {
        val phone = cleanPhone(whatsappInput)
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Informe o WhatsApp para gerar o PIX."))
        }

        try {
            val url = URL("$API_BASE_URL/license/create-pix")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val reqBody = JSONObject().apply {
                put("whatsapp", phone)
                put("plan", plan)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                return@withContext Result.failure(RuntimeException("Erro ao gerar PIX ($code): $err"))
            }

            val respText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(respText)
            val pix = json.optJSONObject("pix") ?: return@withContext Result.failure(RuntimeException("Formato de PIX inválido"))

            val data = PixChargeData(
                paymentId = pix.optString("paymentId"),
                value = pix.optDouble("value", 9.90),
                dueDate = pix.optString("dueDate", ""),
                qrCodeBase64 = pix.optString("encodedImage", ""),
                copyPastePix = pix.optString("payload", "")
            )

            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyPaymentOnline(
        context: Context,
        paymentId: String,
        whatsapp: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE_URL/license/check-payment/$paymentId?phone=${cleanPhone(whatsapp)}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(RuntimeException("Falha ao consultar pagamento ($code)"))
            }

            val respText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(respText)
            val confirmed = json.optBoolean("confirmed", false)

            if (confirmed) {
                // Refresh local status
                checkOrActivateOnline(context, whatsapp, "check")
            }

            Result.success(confirmed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
