package com.auroraclima.app.weather.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos de la fachada de clima. Se mantiene SIN cifrar a propósito:
 * solo guarda ciudad y clima (datos no sensibles) y una BD de clima en claro
 * es lo esperable en una app meteorológica (RNF-05). Los datos protegidos por
 * RNF-01 irán en una BD aparte con SQLCipher. Ver mini-ADR en docs/ARQUITECTURA.md.
 */
@Database(
    entities = [SelectedCityEntity::class, WeatherSnapshotEntity::class, DailyForecastEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        const val NAME = "weather.db"

        /** v2 (RF-04): tabla del pronóstico extendido; la ciudad y el clima guardados se conservan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_forecast` (" +
                        "`dayIndex` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`weatherCode` INTEGER NOT NULL, " +
                        "`maxTemperatureC` REAL NOT NULL, " +
                        "`minTemperatureC` REAL NOT NULL, " +
                        "`rainProbabilityPercent` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`dayIndex`))"
                )
            }
        }
    }
}
