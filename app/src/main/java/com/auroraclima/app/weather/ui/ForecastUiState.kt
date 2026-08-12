package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.WeatherCondition

/** Estado inmutable de la pantalla de pronóstico extendido (RF-04, mockup P03). */
data class ForecastUiState(
    val cityName: String = "",
    val days: List<ForecastDayUiState> = emptyList()
)

/** Una fila del listado de 5 días, con los textos ya formateados. */
data class ForecastDayUiState(
    val isToday: Boolean,
    val dayLabel: String,
    val condition: WeatherCondition,
    val rainProbability: String,
    val maxTemperature: String,
    val minTemperature: String,
    // Grados enteros para ubicar la barra de temperatura (v2-03) sin parsear
    // los textos ya formateados; los llena el mapper igual que el resto.
    val maxDegrees: Int = 0,
    val minDegrees: Int = 0
)
