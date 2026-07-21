package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.DailyForecast
import com.auroraclima.app.weather.domain.model.WeatherCondition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del mapeo de una fila del pronóstico (RF-04, mockup P03). */
class ForecastMapperTest {
    @Test
    fun `una fila queda con los textos del mockup P03`() {
        val forecast = DailyForecast(
            date = LocalDate.of(2026, 7, 7),
            condition = WeatherCondition.RAIN,
            maxTemperatureC = 26.4,
            minTemperatureC = 18.2,
            rainProbabilityPercent = 80
        )

        val row = forecast.toDayUiState(today = LocalDate.of(2026, 7, 6))

        assertFalse(row.isToday)
        assertEquals("mar 7", row.dayLabel)
        assertEquals(WeatherCondition.RAIN, row.condition)
        assertEquals("80 %", row.rainProbability)
        assertEquals("26°", row.maxTemperature)
        assertEquals("18°", row.minTemperature)
    }

    @Test
    fun `el dia de hoy se marca como tal`() {
        val today = LocalDate.of(2026, 7, 6)
        val forecast = DailyForecast(
            date = today,
            condition = WeatherCondition.PARTLY_CLOUDY,
            maxTemperatureC = 28.1,
            minTemperatureC = 19.4,
            rainProbabilityPercent = 55
        )

        assertTrue(forecast.toDayUiState(today).isToday)
    }

    @Test
    fun `la fecha corta sigue el formato del mockup sin punto de abreviatura`() {
        // "mié." en el locale es queda como "mié 8"
        assertEquals("mié 8", formatForecastDay(LocalDate.of(2026, 7, 8)))
        assertEquals("vie 10", formatForecastDay(LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun `las temperaturas se redondean al grado entero`() {
        val forecast = DailyForecast(
            date = LocalDate.of(2026, 7, 9),
            condition = WeatherCondition.CLEAR,
            maxTemperatureC = 27.5,
            minTemperatureC = 18.5,
            rainProbabilityPercent = 0
        )

        val row = forecast.toDayUiState(today = LocalDate.of(2026, 7, 6))

        assertEquals("28°", row.maxTemperature)
        assertEquals("19°", row.minTemperature)
    }
}
