package com.auroraclima.app.weather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Servicio de pronóstico de Open-Meteo (sin API key). Base: https://api.open-meteo.com/ */
interface ForecastApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
        const val DAILY_FIELDS = "temperature_2m_max,temperature_2m_min,precipitation_probability_max"
    }
}

@Serializable
data class ForecastResponseDto(
    val current: CurrentWeatherDto,
    val daily: DailyWeatherDto
)

@Serializable
data class CurrentWeatherDto(
    @SerialName("temperature_2m") val temperatureC: Double,
    @SerialName("relative_humidity_2m") val humidityPercent: Int,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeedKmh: Double
)

@Serializable
data class DailyWeatherDto(
    @SerialName("temperature_2m_max") val maxTemperaturesC: List<Double>,
    @SerialName("temperature_2m_min") val minTemperaturesC: List<Double>,
    @SerialName("precipitation_probability_max") val rainProbabilitiesPercent: List<Int?> = emptyList()
)
