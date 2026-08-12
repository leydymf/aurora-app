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
        // Grados enteros de la barra de temperatura (v2-03), con el mismo redondeo
        assertEquals(26, row.maxDegrees)
        assertEquals(18, row.minDegrees)
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
    fun `la barra ubica cada dia dentro del rango termico de la semana`() {
        // Semana de 18° a 28°: rango 10. mar (18–26) → [0, 0.8]; jue (20–28) → [0.2, 1]
        val days = listOf(
            dayRow(minDegrees = 18, maxDegrees = 26),
            dayRow(minDegrees = 20, maxDegrees = 28)
        )

        val spans = temperatureSpans(days)

        assertEquals(0.0f, spans[0].startFraction, 0.001f)
        assertEquals(0.8f, spans[0].endFraction, 0.001f)
        assertEquals(0.2f, spans[1].startFraction, 0.001f)
        assertEquals(1.0f, spans[1].endFraction, 0.001f)
    }

    @Test
    fun `una semana de temperatura constante no divide por cero`() {
        val spans = temperatureSpans(listOf(dayRow(minDegrees = 20, maxDegrees = 20)))

        assertEquals(0.0f, spans[0].startFraction, 0.001f)
        assertEquals(0.0f, spans[0].endFraction, 0.001f)
    }

    @Test
    fun `sin dias no hay tramos`() {
        assertTrue(temperatureSpans(emptyList()).isEmpty())
    }

    private fun dayRow(minDegrees: Int, maxDegrees: Int) = ForecastDayUiState(
        isToday = false,
        dayLabel = "mar 7",
        condition = WeatherCondition.RAIN,
        rainProbability = "80 %",
        maxTemperature = "$maxDegrees°",
        minTemperature = "$minDegrees°",
        maxDegrees = maxDegrees,
        minDegrees = minDegrees
    )

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
