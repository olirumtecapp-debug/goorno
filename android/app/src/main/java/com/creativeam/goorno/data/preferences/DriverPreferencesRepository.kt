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
import kotlinx.coroutines.flow.map

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

    val configFlow: Flow<DriverFinancialConfig> = context.dataStore.data.map { prefs ->
        DriverFinancialConfig(
            driverName = prefs[Keys.DRIVER_NAME] ?: "Motorista",
            vehicleModel = prefs[Keys.VEHICLE_MODEL] ?: "Carro Padrão",
            fuelConsumptionKmPerLiter = prefs[Keys.FUEL_KM_PER_LITER] ?: 10.5,
            fuelPricePerLiter = prefs[Keys.FUEL_PRICE] ?: 5.89,
            maintenanceCostPerKm = prefs[Keys.MAINTENANCE_PER_KM] ?: 0.25,
            fixedExtraCostPerRide = prefs[Keys.FIXED_EXTRA_COST] ?: 0.0,
            targetGrossPerKm = prefs[Keys.TARGET_GROSS_KM] ?: 2.50,
            targetGrossPerHour = prefs[Keys.TARGET_GROSS_HOUR] ?: 50.00,
            minGrossPerKm = prefs[Keys.MIN_GROSS_KM] ?: 1.80,
            minGrossPerHour = prefs[Keys.MIN_GROSS_HOUR] ?: 35.00,
            targetNetPerKm = prefs[Keys.TARGET_NET_KM] ?: 1.30,
            targetNetPerHour = prefs[Keys.TARGET_NET_HOUR] ?: 28.00,
            yellowToleranceRatio = prefs[Keys.YELLOW_TOLERANCE] ?: 0.80,
            requireGrossPerKm = prefs[Keys.REQ_GROSS_KM] ?: true,
            requireGrossPerHour = prefs[Keys.REQ_GROSS_HOUR] ?: false,
            requireNetPerKm = prefs[Keys.REQ_NET_KM] ?: false,
            requireNetPerHour = prefs[Keys.REQ_NET_HOUR] ?: false,
            requirePassengerRating = prefs[Keys.REQ_PASSENGER_RATING] ?: true,
            forceRedOnBadPassengerRating = prefs[Keys.FORCE_RED_ON_BAD_RATING] ?: false,
            targetGoodPassengerRating = prefs[Keys.TARGET_GOOD_PASSENGER_RATING] ?: 4.85,
            minAcceptablePassengerRating = prefs[Keys.MIN_ACCEPTABLE_PASSENGER_RATING] ?: 4.70,
            minConfidenceThreshold = prefs[Keys.MIN_CONFIDENCE] ?: 0.65,
            overlayOpacityPercent = prefs[Keys.OVERLAY_OPACITY] ?: 92,
            overlayAutoMinimizeSec = (prefs[Keys.OVERLAY_AUTO_MIN_SEC] ?: 5).coerceIn(3, 7),
            overlayDisplayStyle = prefs[Keys.OVERLAY_DISPLAY_STYLE] ?: "BANNER_PRO_STREETVIEW",
            overlayTheme = prefs[Keys.OVERLAY_THEME] ?: "DARK",
            overlayBubblePosition = prefs[Keys.OVERLAY_BUBBLE_POSITION] ?: "BELOW",
            overlayHorizontalPosition = prefs[Keys.OVERLAY_HORIZONTAL_POSITION] ?: "RIGHT",
            showFloatingShortcutBubble = prefs[Keys.SHOW_FLOATING_SHORTCUT_BUBBLE] ?: true,
            showProfitOnOverlay = prefs[Keys.SHOW_PROFIT_ON_OVERLAY] ?: false,
            showCostOnOverlay = prefs[Keys.SHOW_COST_ON_OVERLAY] ?: false,
            riskAlertEnabled = prefs[Keys.RISK_ALERT_ENABLED] ?: true,
            forceRedOnRiskArea = prefs[Keys.FORCE_RED_ON_RISK_AREA] ?: true,
            useDefaultRiskKeywords = prefs[Keys.USE_DEFAULT_RISK_KEYWORDS] ?: true,
            customRiskZones = prefs[Keys.CUSTOM_RISK_ZONES] ?: "",
            favoriteZonesEnabled = prefs[Keys.FAVORITE_ZONES_ENABLED] ?: true,
            customFavoriteZones = prefs[Keys.CUSTOM_FAVORITE_ZONES] ?: "",
            copilotMonitoringEnabled = prefs[Keys.COPILOT_MONITORING_ENABLED] ?: true,
            saveHistoryEnabled = prefs[Keys.SAVE_HISTORY] ?: true,
            cloudBackupConsent = prefs[Keys.CLOUD_BACKUP_CONSENT] ?: false,
            driverRecoveryCode = prefs[Keys.DRIVER_RECOVERY_CODE] ?: ""
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
