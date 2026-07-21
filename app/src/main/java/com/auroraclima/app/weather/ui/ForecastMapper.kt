package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.DailyForecast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    minTemperature = formatDegrees(minTemperatureC)
)

/** Día corto como en el mockup P03: "mar 7". */
internal fun formatForecastDay(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es")))
    .replace(".", "")
