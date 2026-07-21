package com.auroraclima.app.weather.domain.repository

import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.DailyForecast
import com.auroraclima.app.weather.domain.model.WeatherSnapshot

/**
 * Acceso a datos de clima para la fachada (RF-02, RF-03, RF-04).
 * Las operaciones remotas devuelven [Result]; las excepciones no cruzan a la UI.
 */
interface WeatherRepository {
    /** Busca ciudades por nombre en el servicio de geocodificación. */
    suspend fun searchCities(query: String): Result<List<City>>

    /** Ciudad predeterminada recordada entre sesiones, o null si aún no hay. */
    suspend fun getDefaultCity(): City?

    /** Guarda la ciudad seleccionada como predeterminada (RF-02.3). */
    suspend fun saveDefaultCity(city: City)

    /** Último clima almacenado localmente, para mostrar sin conexión (RF-03.2). */
    suspend fun getCachedSnapshot(): WeatherSnapshot?

    /** Pronóstico diario almacenado localmente, en orden cronológico (RF-04). */
    suspend fun getCachedForecast(): List<DailyForecast>

    /** Consulta el clima en red y actualiza la caché local (clima actual y pronóstico). */
    suspend fun refreshSnapshot(city: City): Result<WeatherSnapshot>
}
