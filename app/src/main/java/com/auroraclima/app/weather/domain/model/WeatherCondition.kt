package com.auroraclima.app.weather.domain.model

/** Condición meteorológica derivada del código WMO que entrega Open-Meteo. */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM;

    companion object {
        /** Tabla de códigos WMO 4677 usada por Open-Meteo (`weather_code`). */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDERSTORM
            else -> OVERCAST
        }
    }
}
