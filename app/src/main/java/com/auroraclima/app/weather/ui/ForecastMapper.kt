package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.DailyForecast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Convierte un día del pronóstico en la fila del mockup P03. El día de hoy se
 * marca para que la UI lo rotule con su recurso («Hoy») en lugar de la fecha.
 */
internal fun DailyForecast.toDayUiState(today: LocalDate) = ForecastDayUiState(
    isToday = date == today,
    dayLabel = formatForecastDay(date),
    condition = condition,
    rainProbability = "$rainProbabilityPercent %",
    maxTemperature = formatDegrees(maxTemperatureC),
    minTemperature = formatDegrees(minTemperatureC),
    maxDegrees = maxTemperatureC.roundToInt(),
    minDegrees = minTemperatureC.roundToInt()
)

/** Tramo `[startFraction, endFraction]` de un día dentro del rango térmico de la semana. */
internal data class TemperatureSpan(val startFraction: Float, val endFraction: Float)

/**
 * Ubica cada día sobre el eje térmico común de la semana para la barra de
 * temperatura de P03 (v2-03): 0 es la mínima de la semana y 1 la máxima. Si la
 * semana entera tiene un solo valor, el rango se fuerza a 1 °C para no dividir
 * por cero (la barra pinta entonces un punto).
 */
internal fun temperatureSpans(days: List<ForecastDayUiState>): List<TemperatureSpan> {
    if (days.isEmpty()) return emptyList()
    val weekMin = days.minOf { it.minDegrees }
    val range = (days.maxOf { it.maxDegrees } - weekMin).coerceAtLeast(1)
    return days.map { day ->
        TemperatureSpan(
            startFraction = (day.minDegrees - weekMin).toFloat() / range,
            endFraction = (day.maxDegrees - weekMin).toFloat() / range
        )
    }
}

/** Día corto como en el mockup P03: "mar 7". */
internal fun formatForecastDay(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es")))
    .replace(".", "")
