package com.auroraclima.app.weather.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.auroraclima.app.R
import com.auroraclima.app.weather.domain.model.WeatherCondition

/** Recursos de presentación por condición meteorológica (mockup P01). */
@DrawableRes
internal fun WeatherCondition.iconRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.drawable.ic_weather_sun
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_weather_cloud_sun
    WeatherCondition.OVERCAST -> R.drawable.ic_weather_cloud
    WeatherCondition.FOG -> R.drawable.ic_weather_fog
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> R.drawable.ic_weather_rain
    WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    WeatherCondition.THUNDERSTORM -> R.drawable.ic_weather_storm
}

@StringRes
internal fun WeatherCondition.labelRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_condition_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_condition_overcast
    WeatherCondition.FOG -> R.string.weather_condition_fog
    WeatherCondition.DRIZZLE -> R.string.weather_condition_drizzle
    WeatherCondition.RAIN -> R.string.weather_condition_rain
    WeatherCondition.SNOW -> R.string.weather_condition_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_condition_thunderstorm
}
