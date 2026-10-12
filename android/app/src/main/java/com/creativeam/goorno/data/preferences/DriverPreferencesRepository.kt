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
        val MIN_CONFIDENCE = doublePreferencesKey("min_confidence")
        val OVERLAY_OPACITY = intPreferencesKey("overlay_opacity")
        val OVERLAY_AUTO_MIN_SEC = intPreferencesKey("overlay_auto_min_sec")
        val OVERLAY_DISPLAY_STYLE = stringPreferencesKey("overlay_display_style")
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
            targetGrossPerKm = prefs[Keys.TARGET_GROSS_KM] ?: 2.00,
            targetGrossPerHour = prefs[Keys.TARGET_GROSS_HOUR] ?: 40.00,
            targetNetPerKm = prefs[Keys.TARGET_NET_KM] ?: 1.30,
            targetNetPerHour = prefs[Keys.TARGET_NET_HOUR] ?: 28.00,
            yellowToleranceRatio = prefs[Keys.YELLOW_TOLERANCE] ?: 0.80,
            requireGrossPerKm = prefs[Keys.REQ_GROSS_KM] ?: true,
            requireGrossPerHour = prefs[Keys.REQ_GROSS_HOUR] ?: true,
            requireNetPerKm = prefs[Keys.REQ_NET_KM] ?: false,
            requireNetPerHour = prefs[Keys.REQ_NET_HOUR] ?: false,
            minConfidenceThreshold = prefs[Keys.MIN_CONFIDENCE] ?: 0.65,
            overlayOpacityPercent = prefs[Keys.OVERLAY_OPACITY] ?: 92,
            overlayAutoMinimizeSec = prefs[Keys.OVERLAY_AUTO_MIN_SEC] ?: 7,
            overlayDisplayStyle = prefs[Keys.OVERLAY_DISPLAY_STYLE] ?: "SEMAFORO_COMPACT",
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
            prefs[Keys.TARGET_NET_KM] = config.targetNetPerKm
            prefs[Keys.TARGET_NET_HOUR] = config.targetNetPerHour
            prefs[Keys.YELLOW_TOLERANCE] = config.yellowToleranceRatio
            prefs[Keys.REQ_GROSS_KM] = config.requireGrossPerKm
            prefs[Keys.REQ_GROSS_HOUR] = config.requireGrossPerHour
            prefs[Keys.REQ_NET_KM] = config.requireNetPerKm
            prefs[Keys.REQ_NET_HOUR] = config.requireNetPerHour
            prefs[Keys.MIN_CONFIDENCE] = config.minConfidenceThreshold
            prefs[Keys.OVERLAY_OPACITY] = config.overlayOpacityPercent.coerceIn(25, 100)
            prefs[Keys.OVERLAY_AUTO_MIN_SEC] = config.overlayAutoMinimizeSec.coerceIn(3, 60)
            prefs[Keys.OVERLAY_DISPLAY_STYLE] = config.overlayDisplayStyle
            prefs[Keys.COPILOT_MONITORING_ENABLED] = config.copilotMonitoringEnabled
            prefs[Keys.SAVE_HISTORY] = config.saveHistoryEnabled
            prefs[Keys.CLOUD_BACKUP_CONSENT] = config.cloudBackupConsent
            prefs[Keys.DRIVER_RECOVERY_CODE] = config.driverRecoveryCode.trim()
        }
    }
}
