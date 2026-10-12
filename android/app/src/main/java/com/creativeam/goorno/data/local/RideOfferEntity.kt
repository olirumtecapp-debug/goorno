package com.creativeam.goorno.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade Room para armazenamento local de ofertas reais analisadas.
 * Nunca mistura simulações (isSimulated = false nas consultas de histórico real).
 */
@Entity(tableName = "ride_offers")
data class RideOfferEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val sourceApp: String,
    val captureMethod: String,
    val rideCategory: String?,
    val offeredPrice: Double,
    val actualReceivedPrice: Double? = null,
    val pickupDistanceKm: Double?,
    val tripDistanceKm: Double?,
    val totalDistanceKm: Double?,
    val pickupDurationMin: Int?,
    val tripDurationMin: Int?,
    val totalDurationMin: Int?,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val estimatedTotalCost: Double?,
    val estimatedNetProfit: Double?,
    val netProfitPerKm: Double?,
    val netProfitPerHour: Double?,
    val verdict: String,
    val reasonsSummary: String,
    val originAddress: String?,
    val destinationAddress: String?,
    val confidenceScore: Double,
    val isPartialReading: Boolean,
    val isSimulated: Boolean = false
)
