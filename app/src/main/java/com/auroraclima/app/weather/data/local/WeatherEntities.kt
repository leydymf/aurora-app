package com.auroraclima.app.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ambas tablas son de una sola fila: siempre se lee/escribe este id. */
const val SINGLE_ROW_ID = 0

/** Ciudad predeterminada (RF-02.3). Tabla de una sola fila. */
@Entity(tableName = "selected_city")
data class SelectedCityEntity(
    @PrimaryKey val rowId: Int = SINGLE_ROW_ID,
    val cityId: Long,
    val name: String,
    val region: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

/** Último clima consultado, para mostrarlo sin conexión (RF-03.2). Tabla de una sola fila. */
@Entity(tableName = "weather_snapshot")
data class WeatherSnapshotEntity(
    @PrimaryKey val rowId: Int = SINGLE_ROW_ID,
    val cityName: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val rainProbabilityPercent: Int,
    val updatedAtEpochMillis: Long
)
