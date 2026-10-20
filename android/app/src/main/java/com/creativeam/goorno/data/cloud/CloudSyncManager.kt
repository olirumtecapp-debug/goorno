package com.creativeam.goorno.data.cloud

import android.content.Context
import com.creativeam.goorno.data.license.LicenseManager
import com.creativeam.goorno.data.local.RideOfferEntity
import com.creativeam.goorno.model.DriverFinancialConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gerenciador de Sincronização Automática na Nuvem e Backup/Restauração para Troca de Aparelho.
 * O ID do motorista é seu número de WhatsApp.
 * Ao alterar metas, custos ou áreas de risco, os dados sincronizam automaticamente na nuvem.
 * Ao trocar de celular ou reinstalar o GoOrNo, basta validar o WhatsApp que todo o perfil é restaurado.
 */
object CloudSyncManager {

    private const val FIRESTORE_BASE_URL =
        "https://firestore.googleapis.com/v1/projects/expedicao-brasil/databases/(default)/documents/goorno_cloud_store"

    fun exportToJsonString(
        config: DriverFinancialConfig,
        offers: List<RideOfferEntity>
    ): String {
        val root = JSONObject()
        val cfgObj = JSONObject().apply {
            put("driverName", config.driverName)
            put("vehicleModel", config.vehicleModel)
            put("fuelConsumptionKmPerLiter", config.fuelConsumptionKmPerLiter)
            put("fuelPricePerLiter", config.fuelPricePerLiter)
            put("maintenanceCostPerKm", config.maintenanceCostPerKm)
            put("fixedExtraCostPerRide", config.fixedExtraCostPerRide)
            put("targetGrossPerKm", config.targetGrossPerKm)
            put("targetGrossPerHour", config.targetGrossPerHour)
            put("minGrossPerKm", config.minGrossPerKm)
            put("minGrossPerHour", config.minGrossPerHour)
            put("targetNetPerKm", config.targetNetPerKm)
            put("targetNetPerHour", config.targetNetPerHour)
            put("yellowToleranceRatio", config.yellowToleranceRatio)
            put("requireGrossPerKm", config.requireGrossPerKm)
            put("requireGrossPerHour", config.requireGrossPerHour)
            put("requireNetPerKm", config.requireNetPerKm)
            put("requireNetPerHour", config.requireNetPerHour)
            put("requirePassengerRating", config.requirePassengerRating)
            put("forceRedOnBadPassengerRating", config.forceRedOnBadPassengerRating)
            put("targetGoodPassengerRating", config.targetGoodPassengerRating)
            put("minAcceptablePassengerRating", config.minAcceptablePassengerRating)
            put("minConfidenceThreshold", config.minConfidenceThreshold)
            put("riskAlertEnabled", config.riskAlertEnabled)
            put("forceRedOnRiskArea", config.forceRedOnRiskArea)
            put("useDefaultRiskKeywords", config.useDefaultRiskKeywords)
            put("customRiskZones", config.customRiskZones)
            put("favoriteZonesEnabled", config.favoriteZonesEnabled)
            put("customFavoriteZones", config.customFavoriteZones)
            put("overlayOpacityPercent", config.overlayOpacityPercent)
            put("overlayAutoMinimizeSec", config.overlayAutoMinimizeSec)
            put("overlayDisplayStyle", config.overlayDisplayStyle)
            put("overlayTheme", config.overlayTheme)
            put("overlayBubblePosition", config.overlayBubblePosition)
            put("overlayHorizontalPosition", config.overlayHorizontalPosition)
            put("showFloatingShortcutBubble", config.showFloatingShortcutBubble)
            put("showProfitOnOverlay", config.showProfitOnOverlay)
            put("showCostOnOverlay", config.showCostOnOverlay)
            put("copilotMonitoringEnabled", config.copilotMonitoringEnabled)
            put("saveHistoryEnabled", config.saveHistoryEnabled)
            put("cloudBackupConsent", config.cloudBackupConsent)
            put("driverRecoveryCode", config.driverRecoveryCode)
        }
        root.put("config", cfgObj)

        val arr = JSONArray()
        for (o in offers.take(150)) {
            val item = JSONObject().apply {
                put("id", o.id)
                put("timestampMillis", o.timestampMillis)
                put("sourceApp", o.sourceApp)
                put("captureMethod", o.captureMethod)
                put("rideCategory", o.rideCategory ?: "")
                put("offeredPrice", o.offeredPrice)
                if (o.actualReceivedPrice != null) put("actualReceivedPrice", o.actualReceivedPrice)
                if (o.pickupDistanceKm != null) put("pickupDistanceKm", o.pickupDistanceKm)
                if (o.tripDistanceKm != null) put("tripDistanceKm", o.tripDistanceKm)
                if (o.totalDistanceKm != null) put("totalDistanceKm", o.totalDistanceKm)
                if (o.totalDurationMin != null) put("totalDurationMin", o.totalDurationMin)
                if (o.grossPerKm != null) put("grossPerKm", o.grossPerKm)
                if (o.grossPerHour != null) put("grossPerHour", o.grossPerHour)
                if (o.estimatedTotalCost != null) put("estimatedTotalCost", o.estimatedTotalCost)
                if (o.estimatedNetProfit != null) put("estimatedNetProfit", o.estimatedNetProfit)
                if (o.netProfitPerKm != null) put("netProfitPerKm", o.netProfitPerKm)
                if (o.netProfitPerHour != null) put("netProfitPerHour", o.netProfitPerHour)
                put("verdict", o.verdict)
                put("reasonsSummary", o.reasonsSummary)
                put("originAddress", o.originAddress ?: "")
                put("destinationAddress", o.destinationAddress ?: "")
                put("confidenceScore", o.confidenceScore)
                put("isPartialReading", o.isPartialReading)
            }
            arr.put(item)
        }
        root.put("offers", arr)
        return root.toString()
    }

    fun importFromJsonString(jsonStr: String): Pair<DriverFinancialConfig, List<RideOfferEntity>>? {
        return try {
            val root = JSONObject(jsonStr)
            val c = root.optJSONObject("config") ?: JSONObject()
            val config = DriverFinancialConfig(
                driverName = c.optString("driverName", "Motorista"),
                vehicleModel = c.optString("vehicleModel", "Carro Padrão"),
                fuelConsumptionKmPerLiter = c.optDouble("fuelConsumptionKmPerLiter", 10.5),
                fuelPricePerLiter = c.optDouble("fuelPricePerLiter", 5.89),
                maintenanceCostPerKm = c.optDouble("maintenanceCostPerKm", 0.25),
                fixedExtraCostPerRide = c.optDouble("fixedExtraCostPerRide", 0.0),
                targetGrossPerKm = c.optDouble("targetGrossPerKm", 2.50),
                targetGrossPerHour = c.optDouble("targetGrossPerHour", 50.0),
                minGrossPerKm = c.optDouble("minGrossPerKm", 1.80),
                minGrossPerHour = c.optDouble("minGrossPerHour", 35.0),
                targetNetPerKm = c.optDouble("targetNetPerKm", 1.3),
                targetNetPerHour = c.optDouble("targetNetPerHour", 28.0),
                yellowToleranceRatio = c.optDouble("yellowToleranceRatio", 0.8),
                requireGrossPerKm = c.optBoolean("requireGrossPerKm", true),
                requireGrossPerHour = c.optBoolean("requireGrossPerHour", true),
                requireNetPerKm = c.optBoolean("requireNetPerKm", false),
                requireNetPerHour = c.optBoolean("requireNetPerHour", false),
                requirePassengerRating = c.optBoolean("requirePassengerRating", true),
                forceRedOnBadPassengerRating = c.optBoolean("forceRedOnBadPassengerRating", true),
                targetGoodPassengerRating = c.optDouble("targetGoodPassengerRating", 4.85),
                minAcceptablePassengerRating = c.optDouble("minAcceptablePassengerRating", 4.70),
                minConfidenceThreshold = c.optDouble("minConfidenceThreshold", 0.65),
                riskAlertEnabled = c.optBoolean("riskAlertEnabled", true),
                forceRedOnRiskArea = c.optBoolean("forceRedOnRiskArea", true),
                useDefaultRiskKeywords = c.optBoolean("useDefaultRiskKeywords", true),
                customRiskZones = c.optString("customRiskZones", ""),
                favoriteZonesEnabled = c.optBoolean("favoriteZonesEnabled", true),
                customFavoriteZones = c.optString("customFavoriteZones", ""),
                overlayOpacityPercent = c.optInt("overlayOpacityPercent", 92),
                overlayAutoMinimizeSec = c.optInt("overlayAutoMinimizeSec", 5),
                overlayDisplayStyle = c.optString("overlayDisplayStyle", "BANNER_PRO_STREETVIEW"),
                overlayTheme = c.optString("overlayTheme", "DARK"),
                overlayBubblePosition = c.optString("overlayBubblePosition", "BELOW"),
                overlayHorizontalPosition = c.optString("overlayHorizontalPosition", "RIGHT"),
                showFloatingShortcutBubble = c.optBoolean("showFloatingShortcutBubble", true),
                showProfitOnOverlay = c.optBoolean("showProfitOnOverlay", false),
                showCostOnOverlay = c.optBoolean("showCostOnOverlay", false),
                copilotMonitoringEnabled = c.optBoolean("copilotMonitoringEnabled", true),
                saveHistoryEnabled = c.optBoolean("saveHistoryEnabled", true),
                cloudBackupConsent = c.optBoolean("cloudBackupConsent", true),
                driverRecoveryCode = c.optString("driverRecoveryCode", "")
            )

            val list = mutableListOf<RideOfferEntity>()
            val arr = root.optJSONArray("offers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    RideOfferEntity(
                        id = o.optString("id", "restored-$i"),
                        timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis()),
                        sourceApp = o.optString("sourceApp", "UBER_DRIVER"),
                        captureMethod = o.optString("captureMethod", "ACCESSIBILITY"),
                        rideCategory = o.optString("rideCategory", "").ifBlank { null },
                        offeredPrice = o.optDouble("offeredPrice", 0.0),
                        actualReceivedPrice = if (o.has("actualReceivedPrice")) o.optDouble("actualReceivedPrice") else null,
                        pickupDistanceKm = if (o.has("pickupDistanceKm")) o.optDouble("pickupDistanceKm") else null,
                        tripDistanceKm = if (o.has("tripDistanceKm")) o.optDouble("tripDistanceKm") else null,
                        totalDistanceKm = if (o.has("totalDistanceKm")) o.optDouble("totalDistanceKm") else null,
                        pickupDurationMin = null,
                        tripDurationMin = null,
                        totalDurationMin = if (o.has("totalDurationMin")) o.optInt("totalDurationMin") else null,
                        grossPerKm = if (o.has("grossPerKm")) o.optDouble("grossPerKm") else null,
                        grossPerHour = if (o.has("grossPerHour")) o.optDouble("grossPerHour") else null,
                        estimatedTotalCost = if (o.has("estimatedTotalCost")) o.optDouble("estimatedTotalCost") else null,
                        estimatedNetProfit = if (o.has("estimatedNetProfit")) o.optDouble("estimatedNetProfit") else null,
                        netProfitPerKm = if (o.has("netProfitPerKm")) o.optDouble("netProfitPerKm") else null,
                        netProfitPerHour = if (o.has("netProfitPerHour")) o.optDouble("netProfitPerHour") else null,
                        verdict = o.optString("verdict", "GREEN"),
                        reasonsSummary = o.optString("reasonsSummary", ""),
                        originAddress = o.optString("originAddress", "").ifBlank { null },
                        destinationAddress = o.optString("destinationAddress", "").ifBlank { null },
                        confidenceScore = o.optDouble("confidenceScore", 0.9),
                        isPartialReading = o.optBoolean("isPartialReading", false),
                        isSimulated = false
                    )
                )
            }
            Pair(config, list)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadBackupByDriverCode(
        driverCode: String,
        config: DriverFinancialConfig,
        offers: List<RideOfferEntity>
    ): Result<String> = withContext(Dispatchers.IO) {
        val sanitizedCode = driverCode.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitizedCode.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Informe um código com pelo menos 4 caracteres."))
        }
        try {
            val payloadJson = exportToJsonString(config.copy(driverRecoveryCode = sanitizedCode, cloudBackupConsent = true), offers)
            val firestoreDoc = JSONObject().apply {
                put(
                    "fields",
                    JSONObject().apply {
                        put("payloadJson", JSONObject().put("stringValue", payloadJson))
                        put("updatedAt", JSONObject().put("stringValue", System.currentTimeMillis().toString()))
                    }
                )
            }

            val url = URL("$FIRESTORE_BASE_URL/driver_$sanitizedCode")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(firestoreDoc.toString())
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success("Backup salvo na nuvem com o código '$sanitizedCode'!")
            } else {
                Result.failure(RuntimeException("Erro HTTP $code ao salvar backup."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackupByDriverCode(
        driverCode: String
    ): Result<Pair<DriverFinancialConfig, List<RideOfferEntity>>> = withContext(Dispatchers.IO) {
        val sanitizedCode = driverCode.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitizedCode.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Informe um código válido."))
        }
        try {
            val url = URL("$FIRESTORE_BASE_URL/driver_$sanitizedCode")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(RuntimeException("Nenhum backup encontrado para o código '$sanitizedCode'."))
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val doc = JSONObject(body)
            val payloadJson = doc.optJSONObject("fields")
                ?.optJSONObject("payloadJson")
                ?.optString("stringValue")
                ?: return@withContext Result.failure(RuntimeException("Formato de backup inválido."))

            val parsed = importFromJsonString(payloadJson)
                ?: return@withContext Result.failure(RuntimeException("Falha ao decodificar backup."))
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sincronização Automática na Nuvem Vinculada ao WhatsApp do Motorista.
     * Salva as configurações de veículo, metas de ganho e áreas de risco sem exigir senhas ou códigos manuais.
     */
    suspend fun autoSyncSettings(
        context: Context,
        config: DriverFinancialConfig,
        offers: List<RideOfferEntity> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val phone = LicenseManager.getSavedWhatsApp(context).ifBlank {
            LicenseManager.cleanPhone(config.driverRecoveryCode)
        }
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("Cadastre seu WhatsApp para ativar a sincronização na nuvem."))
        }
        uploadBackupByDriverCode(phone, config, offers)
    }

    /**
     * Restauração Automática da Nuvem Vinculada ao WhatsApp.
     * Ao trocar de celular ou reinstalar o aplicativo, basta validar o WhatsApp que as configurações
     * (consumo do carro, combustível, metas R$/km e áreas de risco) são restauradas imediatamente.
     */
    suspend fun autoRestoreSettings(
        context: Context,
        whatsappInput: String? = null
    ): Result<Pair<DriverFinancialConfig, List<RideOfferEntity>>> = withContext(Dispatchers.IO) {
        val phone = (whatsappInput ?: LicenseManager.getSavedWhatsApp(context)).let { LicenseManager.cleanPhone(it) }
        if (phone.length < 10) {
            return@withContext Result.failure(IllegalArgumentException("WhatsApp não cadastrado ou inválido."))
        }
        restoreBackupByDriverCode(phone)
    }
}

