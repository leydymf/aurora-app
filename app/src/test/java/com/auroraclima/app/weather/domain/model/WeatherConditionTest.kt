package com.auroraclima.app.weather.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionTest {
    @Test
    fun `mapea los codigos WMO de Open-Meteo a condiciones`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmoCode(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmoCode(2))
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromWmoCode(3))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmoCode(45))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWmoCode(53))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmoCode(63))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmoCode(81))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmoCode(73))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmoCode(95))
    }

    @Test
    fun `un codigo desconocido cae en nublado como valor seguro`() {
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromWmoCode(42))
    }
}
