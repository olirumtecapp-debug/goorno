package com.creativeam.goorno.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creativeam.goorno.model.DriverFinancialConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "goorno_driver_settings")

/**
 * Repositório DataStore para persistência local das configurações financeiras e de privacidade do motorista.
 */
class DriverPreferencesRepository(private val context: Context) {

    private object Keys {
        val DRIVER_NAME = stringPreferencesKey("driver_name")
        val VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
        val FUEL_KM_PER_LITER = doublePreferencesKey("fuel_km_per_liter")
        val FUEL_PRICE = doublePreferencesKey("fuel_price")
        val MAINTENANCE_PER_KM = doublePreferencesKey("maintenance_per_km")
        val FIXED_EXTRA_COST = doublePreferencesKey("fixed_extra_cost")
        val TARGET_GROSS_KM = doublePreferencesKey("target_gross_km")
        val TARGET_GROSS_HOUR = doublePreferencesKey("target_gross_hour")
        val TARGET_NET_KM = doublePreferencesKey("target_net_km")
        val TARGET_NET_HOUR = doublePreferencesKey("target_net_hour")
        val YELLOW_TOLERANCE = doublePreferencesKey("yellow_tolerance")
        val REQ_GROSS_KM = booleanPreferencesKey("req_gross_km")
        val REQ_GROSS_HOUR = booleanPreferencesKey("req_gross_hour")
        val REQ_NET_KM = booleanPreferencesKey("req_net_km")
        val REQ_NET_HOUR = booleanPreferencesKey("req_net_hour")
        val REQ_PASSENGER_RATING = booleanPreferencesKey("req_passenger_rating")
        val FORCE_RED_ON_BAD_RATING = booleanPreferencesKey("force_red_on_bad_rating")
        val TARGET_GOOD_PASSENGER_RATING = doublePreferencesKey("target_good_passenger_rating")
        val MIN_ACCEPTABLE_PASSENGER_RATING = doublePreferencesKey("min_acceptable_passenger_rating")
        val MIN_CONFIDENCE = doublePreferencesKey("min_confidence")
        val OVERLAY_OPACITY = intPreferencesKey("overlay_opacity")
        val OVERLAY_AUTO_MIN_SEC = intPreferencesKey("overlay_auto_min_sec")
        val OVERLAY_DISPLAY_STYLE = stringPreferencesKey("overlay_display_style")
        val OVERLAY_THEME = stringPreferencesKey("overlay_theme")
        val OVERLAY_BUBBLE_POSITION = stringPreferencesKey("overlay_bubble_position")
        val OVERLAY_HORIZONTAL_POSITION = stringPreferencesKey("overlay_horizontal_position")
        val MIN_GROSS_KM = doublePreferencesKey("min_gross_km")
        val MIN_GROSS_HOUR = doublePreferencesKey("min_gross_hour")
        val SHOW_FLOATING_SHORTCUT_BUBBLE = booleanPreferencesKey("show_floating_shortcut_bubble")
        val SHOW_PROFIT_ON_OVERLAY = booleanPreferencesKey("show_profit_on_overlay")
        val SHOW_COST_ON_OVERLAY = booleanPreferencesKey("show_cost_on_overlay")
        val RISK_ALERT_ENABLED = booleanPreferencesKey("risk_alert_enabled")
        val FORCE_RED_ON_RISK_AREA = booleanPreferencesKey("force_red_on_risk_area")
        val USE_DEFAULT_RISK_KEYWORDS = booleanPreferencesKey("use_default_risk_keywords")
        val CUSTOM_RISK_ZONES = stringPreferencesKey("custom_risk_zones")
        val FAVORITE_ZONES_ENABLED = booleanPreferencesKey("favorite_zones_enabled")
        val CUSTOM_FAVORITE_ZONES = stringPreferencesKey("custom_favorite_zones")
        val COPILOT_MONITORING_ENABLED = booleanPreferencesKey("copilot_monitoring_enabled")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val CLOUD_BACKUP_CONSENT = booleanPreferencesKey("cloud_backup_consent")
        val DRIVER_RECOVERY_CODE = stringPreferencesKey("driver_recovery_code")
    }

    private val backupPrefs by lazy {
        context.getSharedPreferences("goorno_local_prefs_backup", Context.MODE_PRIVATE)
    }

    val configFlow: Flow<DriverFinancialConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val b = backupPrefs
            DriverFinancialConfig(
                driverName = prefs[Keys.DRIVER_NAME] ?: b.getString("driver_name", null) ?: "Motorista",
                vehicleModel = prefs[Keys.VEHICLE_MODEL] ?: b.getString("vehicle_model", null) ?: "Carro Padrão",
                fuelConsumptionKmPerLiter = prefs[Keys.FUEL_KM_PER_LITER] ?: if (b.contains("fuel_km_per_liter")) b.getFloat("fuel_km_per_liter", 10.5f).toDouble() else 10.5,
                fuelPricePerLiter = prefs[Keys.FUEL_PRICE] ?: if (b.contains("fuel_price")) b.getFloat("fuel_price", 5.89f).toDouble() else 5.89,
                maintenanceCostPerKm = prefs[Keys.MAINTENANCE_PER_KM] ?: if (b.contains("maintenance_per_km")) b.getFloat("maintenance_per_km", 0.25f).toDouble() else 0.25,
                fixedExtraCostPerRide = prefs[Keys.FIXED_EXTRA_COST] ?: if (b.contains("fixed_extra_cost")) b.getFloat("fixed_extra_cost", 0.0f).toDouble() else 0.0,
                targetGrossPerKm = prefs[Keys.TARGET_GROSS_KM] ?: if (b.contains("target_gross_km")) b.getFloat("target_gross_km", 2.50f).toDouble() else 2.50,
                targetGrossPerHour = prefs[Keys.TARGET_GROSS_HOUR] ?: if (b.contains("target_gross_hour")) b.getFloat("target_gross_hour", 50.00f).toDouble() else 50.00,
                minGrossPerKm = prefs[Keys.MIN_GROSS_KM] ?: if (b.contains("min_gross_km")) b.getFloat("min_gross_km", 1.80f).toDouble() else 1.80,
                minGrossPerHour = prefs[Keys.MIN_GROSS_HOUR] ?: if (b.contains("min_gross_hour")) b.getFloat("min_gross_hour", 35.00f).toDouble() else 35.00,
                targetNetPerKm = prefs[Keys.TARGET_NET_KM] ?: if (b.contains("target_net_km")) b.getFloat("target_net_km", 1.30f).toDouble() else 1.30,
                targetNetPerHour = prefs[Keys.TARGET_NET_HOUR] ?: if (b.contains("target_net_hour")) b.getFloat("target_net_hour", 28.00f).toDouble() else 28.00,
                yellowToleranceRatio = prefs[Keys.YELLOW_TOLERANCE] ?: if (b.contains("yellow_tolerance")) b.getFloat("yellow_tolerance", 0.80f).toDouble() else 0.80,
                requireGrossPerKm = prefs[Keys.REQ_GROSS_KM] ?: b.getBoolean("req_gross_km", true),
                requireGrossPerHour = prefs[Keys.REQ_GROSS_HOUR] ?: b.getBoolean("req_gross_hour", true),
                requireNetPerKm = prefs[Keys.REQ_NET_KM] ?: b.getBoolean("req_net_km", false),
                requireNetPerHour = prefs[Keys.REQ_NET_HOUR] ?: b.getBoolean("req_net_hour", false),
                requirePassengerRating = prefs[Keys.REQ_PASSENGER_RATING] ?: b.getBoolean("req_passenger_rating", true),
                forceRedOnBadPassengerRating = prefs[Keys.FORCE_RED_ON_BAD_RATING] ?: b.getBoolean("force_red_on_bad_rating", true),
                targetGoodPassengerRating = prefs[Keys.TARGET_GOOD_PASSENGER_RATING] ?: if (b.contains("target_good_passenger_rating")) b.getFloat("target_good_passenger_rating", 4.85f).toDouble() else 4.85,
                minAcceptablePassengerRating = prefs[Keys.MIN_ACCEPTABLE_PASSENGER_RATING] ?: if (b.contains("min_acceptable_passenger_rating")) b.getFloat("min_acceptable_passenger_rating", 4.70f).toDouble() else 4.70,
                minConfidenceThreshold = prefs[Keys.MIN_CONFIDENCE] ?: if (b.contains("min_confidence")) b.getFloat("min_confidence", 0.65f).toDouble() else 0.65,
                overlayOpacityPercent = prefs[Keys.OVERLAY_OPACITY] ?: b.getInt("overlay_opacity", 92),
                overlayAutoMinimizeSec = (prefs[Keys.OVERLAY_AUTO_MIN_SEC] ?: b.getInt("overlay_auto_min_sec", 5)).coerceIn(3, 7),
                overlayDisplayStyle = prefs[Keys.OVERLAY_DISPLAY_STYLE] ?: b.getString("overlay_display_style", null) ?: "BANNER_PRO_STREETVIEW",
                overlayTheme = prefs[Keys.OVERLAY_THEME] ?: b.getString("overlay_theme", null) ?: "DARK",
                overlayBubblePosition = prefs[Keys.OVERLAY_BUBBLE_POSITION] ?: b.getString("overlay_bubble_position", null) ?: "BELOW",
                overlayHorizontalPosition = prefs[Keys.OVERLAY_HORIZONTAL_POSITION] ?: b.getString("overlay_horizontal_position", null) ?: "RIGHT",
                showFloatingShortcutBubble = prefs[Keys.SHOW_FLOATING_SHORTCUT_BUBBLE] ?: b.getBoolean("show_floating_shortcut_bubble", true),
                showProfitOnOverlay = prefs[Keys.SHOW_PROFIT_ON_OVERLAY] ?: b.getBoolean("show_profit_on_overlay", false),
                showCostOnOverlay = prefs[Keys.SHOW_COST_ON_OVERLAY] ?: b.getBoolean("show_cost_on_overlay", false),
                riskAlertEnabled = prefs[Keys.RISK_ALERT_ENABLED] ?: b.getBoolean("risk_alert_enabled", true),
                forceRedOnRiskArea = prefs[Keys.FORCE_RED_ON_RISK_AREA] ?: b.getBoolean("force_red_on_risk_area", true),
                useDefaultRiskKeywords = prefs[Keys.USE_DEFAULT_RISK_KEYWORDS] ?: b.getBoolean("use_default_risk_keywords", true),
                customRiskZones = prefs[Keys.CUSTOM_RISK_ZONES] ?: b.getString("custom_risk_zones", null) ?: "",
                favoriteZonesEnabled = prefs[Keys.FAVORITE_ZONES_ENABLED] ?: b.getBoolean("favorite_zones_enabled", true),
                customFavoriteZones = prefs[Keys.CUSTOM_FAVORITE_ZONES] ?: b.getString("custom_favorite_zones", null) ?: "",
                copilotMonitoringEnabled = prefs[Keys.COPILOT_MONITORING_ENABLED] ?: b.getBoolean("copilot_monitoring_enabled", true),
                saveHistoryEnabled = prefs[Keys.SAVE_HISTORY] ?: b.getBoolean("save_history", true),
                cloudBackupConsent = prefs[Keys.CLOUD_BACKUP_CONSENT] ?: b.getBoolean("cloud_backup_consent", false),
                driverRecoveryCode = prefs[Keys.DRIVER_RECOVERY_CODE] ?: b.getString("driver_recovery_code", null) ?: ""
            )
        }

    suspend fun getConfigOnce(): DriverFinancialConfig = configFlow.first()

    suspend fun saveConfig(config: DriverFinancialConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRIVER_NAME] = config.driverName
            prefs[Keys.VEHICLE_MODEL] = config.vehicleModel
            prefs[Keys.FUEL_KM_PER_LITER] = config.fuelConsumptionKmPerLiter
            prefs[Keys.FUEL_PRICE] = config.fuelPricePerLiter
            prefs[Keys.MAINTENANCE_PER_KM] = config.maintenanceCostPerKm
            prefs[Keys.FIXED_EXTRA_COST] = config.fixedExtraCostPerRide
            prefs[Keys.TARGET_GROSS_KM] = config.targetGrossPerKm
            prefs[Keys.TARGET_GROSS_HOUR] = config.targetGrossPerHour
            prefs[Keys.MIN_GROSS_KM] = config.minGrossPerKm
            prefs[Keys.MIN_GROSS_HOUR] = config.minGrossPerHour
            prefs[Keys.TARGET_NET_KM] = config.targetNetPerKm
            prefs[Keys.TARGET_NET_HOUR] = config.targetNetPerHour
            prefs[Keys.YELLOW_TOLERANCE] = config.yellowToleranceRatio
            prefs[Keys.REQ_GROSS_KM] = config.requireGrossPerKm
            prefs[Keys.REQ_GROSS_HOUR] = config.requireGrossPerHour
            prefs[Keys.REQ_NET_KM] = config.requireNetPerKm
            prefs[Keys.REQ_NET_HOUR] = config.requireNetPerHour
            prefs[Keys.REQ_PASSENGER_RATING] = config.requirePassengerRating
            prefs[Keys.FORCE_RED_ON_BAD_RATING] = config.forceRedOnBadPassengerRating
            prefs[Keys.TARGET_GOOD_PASSENGER_RATING] = config.targetGoodPassengerRating.coerceIn(3.0, 5.0)
            prefs[Keys.MIN_ACCEPTABLE_PASSENGER_RATING] = config.minAcceptablePassengerRating.coerceIn(3.0, 5.0)
            prefs[Keys.MIN_CONFIDENCE] = config.minConfidenceThreshold
            prefs[Keys.OVERLAY_OPACITY] = config.overlayOpacityPercent.coerceIn(25, 100)
            prefs[Keys.OVERLAY_AUTO_MIN_SEC] = config.overlayAutoMinimizeSec.coerceIn(3, 60)
            prefs[Keys.OVERLAY_DISPLAY_STYLE] = config.overlayDisplayStyle
            prefs[Keys.OVERLAY_THEME] = config.overlayTheme
            prefs[Keys.OVERLAY_BUBBLE_POSITION] = config.overlayBubblePosition
            prefs[Keys.OVERLAY_HORIZONTAL_POSITION] = config.overlayHorizontalPosition
            prefs[Keys.SHOW_FLOATING_SHORTCUT_BUBBLE] = config.showFloatingShortcutBubble
            prefs[Keys.SHOW_PROFIT_ON_OVERLAY] = config.showProfitOnOverlay
            prefs[Keys.SHOW_COST_ON_OVERLAY] = config.showCostOnOverlay
            prefs[Keys.RISK_ALERT_ENABLED] = config.riskAlertEnabled
            prefs[Keys.FORCE_RED_ON_RISK_AREA] = config.forceRedOnRiskArea
            prefs[Keys.USE_DEFAULT_RISK_KEYWORDS] = config.useDefaultRiskKeywords
            prefs[Keys.CUSTOM_RISK_ZONES] = config.customRiskZones
            prefs[Keys.FAVORITE_ZONES_ENABLED] = config.favoriteZonesEnabled
            prefs[Keys.CUSTOM_FAVORITE_ZONES] = config.customFavoriteZones
            prefs[Keys.COPILOT_MONITORING_ENABLED] = config.copilotMonitoringEnabled
            prefs[Keys.SAVE_HISTORY] = config.saveHistoryEnabled
            prefs[Keys.CLOUD_BACKUP_CONSENT] = config.cloudBackupConsent
            prefs[Keys.DRIVER_RECOVERY_CODE] = config.driverRecoveryCode.trim()
        }

        // Blindagem Local Dupla: espelho imediato em SharedPreferences para imunidade a atualizações de APK
        try {
            backupPrefs.edit().apply {
                putString("driver_name", config.driverName)
                putString("vehicle_model", config.vehicleModel)
                putFloat("fuel_km_per_liter", config.fuelConsumptionKmPerLiter.toFloat())
                putFloat("fuel_price", config.fuelPricePerLiter.toFloat())
                putFloat("maintenance_per_km", config.maintenanceCostPerKm.toFloat())
                putFloat("fixed_extra_cost", config.fixedExtraCostPerRide.toFloat())
                putFloat("target_gross_km", config.targetGrossPerKm.toFloat())
                putFloat("target_gross_hour", config.targetGrossPerHour.toFloat())
                putFloat("min_gross_km", config.minGrossPerKm.toFloat())
                putFloat("min_gross_hour", config.minGrossPerHour.toFloat())
                putFloat("target_net_km", config.targetNetPerKm.toFloat())
                putFloat("target_net_hour", config.targetNetPerHour.toFloat())
                putFloat("yellow_tolerance", config.yellowToleranceRatio.toFloat())
                putBoolean("req_gross_km", config.requireGrossPerKm)
                putBoolean("req_gross_hour", config.requireGrossPerHour)
                putBoolean("req_net_km", config.requireNetPerKm)
                putBoolean("req_net_hour", config.requireNetPerHour)
                putBoolean("req_passenger_rating", config.requirePassengerRating)
                putBoolean("force_red_on_bad_rating", config.forceRedOnBadPassengerRating)
                putFloat("target_good_passenger_rating", config.targetGoodPassengerRating.toFloat())
                putFloat("min_acceptable_passenger_rating", config.minAcceptablePassengerRating.toFloat())
                putFloat("min_confidence", config.minConfidenceThreshold.toFloat())
                putInt("overlay_opacity", config.overlayOpacityPercent)
                putInt("overlay_auto_min_sec", config.overlayAutoMinimizeSec)
                putString("overlay_display_style", config.overlayDisplayStyle)
                putString("overlay_theme", config.overlayTheme)
                putString("overlay_bubble_position", config.overlayBubblePosition)
                putString("overlay_horizontal_position", config.overlayHorizontalPosition)
                putBoolean("show_floating_shortcut_bubble", config.showFloatingShortcutBubble)
                putBoolean("show_profit_on_overlay", config.showProfitOnOverlay)
                putBoolean("show_cost_on_overlay", config.showCostOnOverlay)
                putBoolean("risk_alert_enabled", config.riskAlertEnabled)
                putBoolean("force_red_on_risk_area", config.forceRedOnRiskArea)
                putBoolean("use_default_risk_keywords", config.useDefaultRiskKeywords)
                putString("custom_risk_zones", config.customRiskZones)
                putBoolean("favorite_zones_enabled", config.favoriteZonesEnabled)
                putString("custom_favorite_zones", config.customFavoriteZones)
                putBoolean("copilot_monitoring_enabled", config.copilotMonitoringEnabled)
                putBoolean("save_history", config.saveHistoryEnabled)
                putBoolean("cloud_backup_consent", config.cloudBackupConsent)
                putString("driver_recovery_code", config.driverRecoveryCode.trim())
                apply()
            }
        } catch (_: Throwable) {
        }

        // Sincronização automática e transparente na nuvem vinculada ao WhatsApp do motorista
        try {
            val phone = com.creativeam.goorno.data.license.LicenseManager.getSavedWhatsApp(context)
            if (phone.length >= 10) {
                com.creativeam.goorno.data.cloud.CloudSyncManager.uploadBackupByDriverCode(
                    driverCode = phone,
                    config = config,
                    offers = emptyList()
                )
            }
        } catch (_: Exception) {
            // Silencioso se offline, garante que a persistência local nunca falhe
        }
    }
}
