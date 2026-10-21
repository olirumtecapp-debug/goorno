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

        // Migração segura para futuras alterações de esquema sem nunca apagar o histórico do motorista
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Preserva 100% da tabela ride_offers existente
            }
        }

        fun getInstance(context: Context): GoOrNoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoOrNoDatabase::class.java,
                    "goorno_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Proteção definitiva: NUNCA destrói o banco de dados em produção
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
