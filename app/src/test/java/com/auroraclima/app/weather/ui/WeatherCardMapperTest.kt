package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.WeatherCondition
import com.auroraclima.app.weather.domain.model.WeatherSnapshot
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCardMapperTest {
    @Test
    fun `formatea la tarjeta como en el mockup P01`() {
        val snapshot = WeatherSnapshot(
            cityName = "Bucaramanga",
            temperatureC = 24.3,
            condition = WeatherCondition.PARTLY_CLOUDY,
            maxTemperatureC = 28.1,
            minTemperatureC = 19.4,
            humidityPercent = 76,
            windSpeedKmh = 9.2,
            rainProbabilityPercent = 55,
            // 2026-07-03 09:28 hora de Bogotá (14:28 UTC)
            updatedAtEpochMillis = 1_783_088_880_000L
        )

        val card = snapshot.toCardUiState(zoneId = ZoneId.of("America/Bogota"))

        assertEquals("Bucaramanga", card.cityName)
        assertEquals("24°", card.temperature)
        assertEquals("28°", card.maxTemperature)
        assertEquals("19°", card.minTemperature)
        assertEquals("76 %", card.humidity)
        assertEquals("9 km/h", card.wind)
        assertEquals("55 %", card.rainProbability)
        assertTrue(
            "hora esperada en updatedAtTime: ${card.updatedAtTime}",
            card.updatedAtTime.contains("9:28")
        )
    }

    @Test
    fun `los grados se redondean al entero mas cercano`() {
        assertEquals("24°", formatDegrees(24.4))
        assertEquals("25°", formatDegrees(24.5))
        assertEquals("-3°", formatDegrees(-2.6))
    }
}
