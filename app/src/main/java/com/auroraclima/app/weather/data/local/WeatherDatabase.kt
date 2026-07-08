package com.auroraclima.app.weather.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos de la fachada de clima. Se mantiene SIN cifrar a propósito:
 * solo guarda ciudad y clima (datos no sensibles) y una BD de clima en claro
 * es lo esperable en una app meteorológica (RNF-05). Los datos protegidos por
 * RNF-01 irán en una BD aparte con SQLCipher. Ver mini-ADR en docs/ARQUITECTURA.md.
 */
@Database(
    entities = [SelectedCityEntity::class, WeatherSnapshotEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        const val NAME = "weather.db"
    }
}
