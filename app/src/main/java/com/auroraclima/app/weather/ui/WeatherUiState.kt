package com.auroraclima.app.weather.ui

/**
 * Estado inmutable de la pantalla principal de clima (RF-01).
 * Por ahora solo estado vacío: la búsqueda (RF-02) y el clima actual (RF-03)
 * se agregan en las siguientes sesiones.
 */
data class WeatherUiState(
    val searchQuery: String = "",
    val headerDate: String = ""
)
