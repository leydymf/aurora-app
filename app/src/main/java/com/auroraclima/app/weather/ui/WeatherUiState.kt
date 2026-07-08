package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition

/**
 * Estado inmutable de la pantalla principal de clima (RF-01, RF-02, RF-03).
 * Todo el texto de la barra de búsqueda vive aquí: esa barra será también la
 * puerta del código secreto (RF-12), así que ningún otro componente lo maneja.
 */
data class WeatherUiState(
    val searchQuery: String = "",
    val headerDate: String = "",
    val searchResults: List<City> = emptyList(),
    val isSearchActive: Boolean = false,
    val showNoResults: Boolean = false,
    val weather: WeatherCardUiState? = null
)

/** Datos ya formateados de la tarjeta de clima actual (mockup P01). */
data class WeatherCardUiState(
    val cityName: String,
    val temperature: String,
    val condition: WeatherCondition,
    val maxTemperature: String,
    val minTemperature: String,
    val updatedAtTime: String,
    val humidity: String,
    val wind: String,
    val rainProbability: String
)
