package com.creativeam.goorno.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RideOfferEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GoOrNoDatabase : RoomDatabase() {
    abstract fun rideOfferDao(): RideOfferDao

    companion object {
        @Volatile
        private var INSTANCE: GoOrNoDatabase? = null

        fun getInstance(context: Context): GoOrNoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoOrNoDatabase::class.java,
                    "goorno_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
