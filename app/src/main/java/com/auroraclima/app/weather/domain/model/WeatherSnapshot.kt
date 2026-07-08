package com.auroraclima.app.weather.domain.model

/**
 * Clima actual de la ciudad seleccionada (RF-03). `updatedAtEpochMillis` permite
 * mostrar la hora de actualización cuando se sirve desde caché sin conexión.
 */
data class WeatherSnapshot(
    val cityName: String,
    val temperatureC: Double,
    val condition: WeatherCondition,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val rainProbabilityPercent: Int,
    val updatedAtEpochMillis: Long
)
