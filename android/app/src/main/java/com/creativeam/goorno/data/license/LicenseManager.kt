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
    val requiresPin: Boolean = false,
    val pinInvalid: Boolean = false,
    val hasPinSet: Boolean = false,
    val fraudAlert: Boolean = false,
    val whatsapp: String = ""
)

data class PixChargeData(
    val paymentId: String,
    val value: Double,
    val dueDate: String,
    val qrCodeBase64: String,
    val copyPastePix: String,
    val plan: String = "mensal",
    val promoApplied: Boolean = false,
    val renewalPrice: Double = 7.99,
    val periodDays: Int = 30
)

data class SubscriptionPlanItem(
    val id: String,
    val name: String,
    val periodDays: Int,
    val normalPrice: Double,
    val promoPrice: Double,
    val renewalPrice: Double,
    val monthlyEquivalentNormal: Double,
    val monthlyEquivalentPromo: Double,
    val savingsPromoVsMonthlyNormal: Double,
    val highlightBadge: String? = null,
    val description: String,
    val isPromoApplied: Boolean = true
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

    private const val KEY_SAVED_PIN = "saved_pin"
    private const val KEY_SAVED_RECOVERY = "saved_recovery"

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

    fun getSavedPin(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED_PIN, "") ?: ""
    }

    fun savePin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SAVED_PIN, pin.trim()).apply()
    }

    fun getSavedRecovery(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED_RECOVERY, "") ?: ""
    }

    fun saveRecovery(context: Context, secret: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SAVED_RECOVERY, secret.trim().uppercase()).apply()
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
        whatsapp: String,
        action: String = "check",
        pin: String? = null,
        recoverySecret: String? = null
    ): Result<LicenseState> = withContext(Dispatchers.IO) {
        val phone = cleanPhone(whatsapp)
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Informe o WhatsApp com DDD e pelo menos 10 dígitos."))
        }

        saveWhatsApp(context, phone)
        val effectivePin = (pin ?: getSavedPin(context)).trim()
        val effectiveRecovery = (recoverySecret ?: getSavedRecovery(context)).trim()

        if (effectivePin.isNotBlank()) savePin(context, effectivePin)
        if (effectiveRecovery.isNotBlank()) saveRecovery(context, effectiveRecovery)

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
                if (pin.isNotBlank()) put("pin", pin)
                if (recovery.isNotBlank()) put("recoverySecret", recovery)
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
            val requiresPin = json.optBoolean("requiresPin", false)
            val pinInvalid = json.optBoolean("pinInvalid", false)
            val hasPinSet = json.optBoolean("hasPinSet", false)
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
                requiresPin = requiresPin,
                pinInvalid = pinInvalid,
                hasPinSet = hasPinSet,
                fraudAlert = fraudAlert,
                whatsapp = phone
            )

            // Restauração Automática das Configurações do Motorista na Nuvem (se existirem)
            if (allowed) {
                try {
                    val restoreRes = com.creativeam.goorno.data.cloud.CloudSyncManager.autoRestoreSettings(context, phone)
                    restoreRes.onSuccess { (restoredCfg, _) ->
                        com.creativeam.goorno.data.preferences.DriverPreferencesRepository(context).saveConfig(restoredCfg)
                    }
                } catch (_: Exception) {
                    // Silencioso se não houver backup prévio ou se estiver offline
                }
            }

            Result.success(state)
        } catch (e: Exception) {
            // Em caso de falha de conexão, usa cache se existir
            val cached = getCachedLicenseState(context)
            Result.success(cached.copy(message = "Offline: usando status salvo no aparelho."))
        }
    }

    /**
     * Redefine o PIN de 4 dígitos sem código aleatório, validando a Palavra-chave ou Placa do motorista.
     */
    suspend fun recoverPinOnline(
        context: Context,
        whatsappInput: String,
        recoverySecretInput: String,
        newPinInput: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val phone = cleanPhone(whatsappInput)
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Informe o WhatsApp com DDD."))
        }
        val secret = recoverySecretInput.trim().toUpperCase()
        if (secret.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Informe sua Palavra-chave ou Placa cadastrada."))
        }
        val pin = newPinInput.trim()
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("O novo PIN deve conter exatamente 4 números."))
        }

        try {
            val url = URL("$API_BASE_URL/license/recover-pin")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val reqBody = JSONObject().apply {
                put("whatsapp", phone)
                put("recoverySecret", secret)
                put("newPin", pin)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val respText = responseStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            val json = JSONObject(respText)
            val ok = json.optBoolean("ok", false)
            val msg = json.optString("message", json.optString("error", "Erro ao redefinir PIN."))

            if (ok) {
                savePin(context, pin)
                saveRecovery(context, secret)
                Result.success(msg)
            } else {
                Result.failure(RuntimeException(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                value = pix.optDouble("value", 3.99),
                dueDate = pix.optString("dueDate", ""),
                qrCodeBase64 = pix.optString("encodedImage", ""),
                copyPastePix = pix.optString("payload", ""),
                plan = pix.optString("plan", plan),
                promoApplied = pix.optBoolean("promoApplied", false),
                renewalPrice = pix.optDouble("renewalPrice", 7.99),
                periodDays = pix.optInt("periodDays", 30)
            )

            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSubscriptionPlans(phone: String? = null): Result<Pair<Boolean, List<SubscriptionPlanItem>>> = withContext(Dispatchers.IO) {
        try {
            val q = if (!phone.isNullOrBlank()) "?phone=${cleanPhone(phone)}" else ""
            val url = URL("$API_BASE_URL/subscription/plans$q")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val eligible = json.optBoolean("isUserEligibleForPromo", true)
                val arr = json.optJSONArray("plans")
                val list = mutableListOf<SubscriptionPlanItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            SubscriptionPlanItem(
                                id = item.optString("id"),
                                name = item.optString("name"),
                                periodDays = item.optInt("periodDays", 30),
                                normalPrice = item.optDouble("normalPrice", 7.99),
                                promoPrice = item.optDouble("promoPrice", 3.99),
                                renewalPrice = item.optDouble("renewalPrice", 7.99),
                                monthlyEquivalentNormal = item.optDouble("monthlyEquivalentNormal", 7.99),
                                monthlyEquivalentPromo = item.optDouble("monthlyEquivalentPromo", 3.99),
                                savingsPromoVsMonthlyNormal = item.optDouble("savingsPromoVsMonthlyNormal", 0.0),
                                highlightBadge = item.optString("highlightBadge").takeIf { it.isNotBlank() },
                                description = item.optString("description", ""),
                                isPromoApplied = item.optBoolean("isPromoApplied", true)
                            )
                        )
                    }
                }
                Result.success(Pair(eligible, list))
            } else {
                Result.success(Pair(true, getFallbackPlans()))
            }
        } catch (_: Exception) {
            Result.success(Pair(true, getFallbackPlans()))
        }
    }

    fun getFallbackPlans(): List<SubscriptionPlanItem> {
        return listOf(
            SubscriptionPlanItem(
                id = "mensal",
                name = "Plano Mensal",
                periodDays = 30,
                normalPrice = 7.99,
                promoPrice = 3.99,
                renewalPrice = 7.99,
                monthlyEquivalentNormal = 7.99,
                monthlyEquivalentPromo = 3.99,
                savingsPromoVsMonthlyNormal = 4.00,
                description = "Acesso completo por 30 dias. Renovação por R$ 7,99/mês."
            ),
            SubscriptionPlanItem(
                id = "trimestral",
                name = "Plano Trimestral",
                periodDays = 90,
                normalPrice = 21.90,
                promoPrice = 18.90,
                renewalPrice = 21.90,
                monthlyEquivalentNormal = 7.30,
                monthlyEquivalentPromo = 6.30,
                savingsPromoVsMonthlyNormal = 5.07,
                description = "Acesso por 3 meses (90 dias). Renovação por R$ 21,90 a cada 3 meses."
            ),
            SubscriptionPlanItem(
                id = "semestral",
                name = "Plano Semestral",
                periodDays = 180,
                normalPrice = 41.90,
                promoPrice = 34.90,
                renewalPrice = 41.90,
                monthlyEquivalentNormal = 6.98,
                monthlyEquivalentPromo = 5.81,
                savingsPromoVsMonthlyNormal = 13.04,
                description = "Acesso por 6 meses (180 dias). Renovação por R$ 41,90 a cada 6 meses."
            ),
            SubscriptionPlanItem(
                id = "anual",
                name = "Plano Anual",
                periodDays = 365,
                normalPrice = 79.90,
                promoPrice = 59.90,
                renewalPrice = 79.90,
                monthlyEquivalentNormal = 6.66,
                monthlyEquivalentPromo = 4.99,
                savingsPromoVsMonthlyNormal = 35.98,
                highlightBadge = "MAIS ECONÔMICO",
                description = "Acesso por 1 ano (365 dias). Menos de R$ 5,00/mês. Renovação por R$ 79,90/ano."
            )
        )
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

    suspend fun redeemVoucherOnline(
        context: Context,
        whatsapp: String,
        voucherCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val phone = cleanPhone(whatsapp)
            if (phone.length < 10) {
                return@withContext Result.failure(RuntimeException("Informe um número de WhatsApp válido."))
            }
            val code = voucherCode.trim().uppercase()
            if (code.isEmpty()) {
                return@withContext Result.failure(RuntimeException("Informe o código do cupom."))
            }

            val deviceId = getDeviceId(context)
            val url = URL("$API_BASE_URL/redeem-voucher")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
            }

            val reqBody = JSONObject().apply {
                put("whatsapp", phone)
                put("code", code)
                put("deviceId", deviceId)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errJson = try { JSONObject(errText) } catch (_: Exception) { null }
                val errMsg = errJson?.optString("error") ?: "Falha ao resgatar cupom (código $responseCode)"
                return@withContext Result.failure(RuntimeException(errMsg))
            }

            val respText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(respText)
            val message = json.optString("message", "Cupom resgatado com sucesso!")

            saveWhatsApp(context, phone)
            checkOrActivateOnline(context, phone, "check")

            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
