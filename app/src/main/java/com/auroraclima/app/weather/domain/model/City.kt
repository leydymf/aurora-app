package com.auroraclima.app.weather.domain.model

/**
 * Ciudad resultado de la búsqueda por nombre (RF-02).
 *
 * @param region división administrativa (p. ej. "Santander"); puede faltar en la API.
 */
data class City(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double
)
