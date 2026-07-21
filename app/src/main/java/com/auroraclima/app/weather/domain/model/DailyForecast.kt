package com.auroraclima.app.weather.domain.model

import java.time.LocalDate

/** Un día del pronóstico extendido de la ciudad seleccionada (RF-04). */
data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxTemperatureC: Double,
    val minTemperatureC: Double,
    val rainProbabilityPercent: Int
)
