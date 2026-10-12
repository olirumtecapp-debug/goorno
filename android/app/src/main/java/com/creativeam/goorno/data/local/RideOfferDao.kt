package com.creativeam.goorno.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideOfferDao {

    @Query("SELECT * FROM ride_offers WHERE isSimulated = 0 ORDER BY timestampMillis DESC LIMIT 300")
    fun observeRealOffers(): Flow<List<RideOfferEntity>>

    @Query("SELECT * FROM ride_offers WHERE isSimulated = 0 ORDER BY timestampMillis DESC LIMIT 300")
    suspend fun getAllRealOffersOnce(): List<RideOfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: RideOfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<RideOfferEntity>)

    @Query("UPDATE ride_offers SET actualReceivedPrice = :receivedPrice WHERE id = :offerId")
    suspend fun updateActualReceivedPrice(offerId: String, receivedPrice: Double)

    @Query("DELETE FROM ride_offers WHERE id = :offerId")
    suspend fun deleteOfferById(offerId: String)

    @Query("DELETE FROM ride_offers WHERE isSimulated = 0")
    suspend fun clearAllRealOffers()
}
