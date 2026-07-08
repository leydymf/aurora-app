package com.auroraclima.app.weather.ui

import com.auroraclima.app.weather.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Convierte el clima de dominio en textos listos para la tarjeta del mockup P01. */
internal fun WeatherSnapshot.toCardUiState(zoneId: ZoneId = ZoneId.systemDefault()) = WeatherCardUiState(
    cityName = cityName,
    temperature = formatDegrees(temperatureC),
    condition = condition,
    maxTemperature = formatDegrees(maxTemperatureC),
    minTemperature = formatDegrees(minTemperatureC),
    updatedAtTime = formatUpdatedAtTime(updatedAtEpochMillis, zoneId),
    humidity = "$humidityPercent %",
    wind = "${windSpeedKmh.roundToInt()} km/h",
    rainProbability = "$rainProbabilityPercent %"
)

internal fun formatDegrees(valueC: Double): String = "${valueC.roundToInt()}°"

/** Hora local corta como en el mockup P01: "9:28 a. m.". */
internal fun formatUpdatedAtTime(epochMillis: Long, zoneId: ZoneId): String = Instant
    .ofEpochMilli(epochMillis)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es")))
    .lowercase(Locale.forLanguageTag("es"))
