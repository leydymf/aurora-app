package com.auroraclima.app.weather.ui

import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.DailyForecast
import com.auroraclima.app.weather.domain.model.WeatherCondition
import com.auroraclima.app.weather.domain.model.WeatherSnapshot
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pruebas del ViewModel del pronóstico extendido (RF-04, mockup P03). */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeForecastWeatherRepository()

    // lunes 2026-07-06 en Bogotá, como en el mockup P03
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-07-06T14:28:00Z"),
        ZoneId.of("America/Bogota")
    )

    private fun createViewModel() = ForecastViewModel(repository, fixedClock)

    @Test
    fun `muestra el pronostico en cache con la ciudad seleccionada`() = runTest {
        // RF-04 criterio 1: desde la vista de una ciudad, previsión de mínimo 5 días
        repository.defaultCity = bucaramanga()
        repository.cachedForecast = sampleForecast()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bucaramanga", state.cityName)
        assertEquals(5, state.days.size)
    }

    @Test
    fun `el dia de hoy se marca y los demas llevan fecha corta`() = runTest {
        repository.defaultCity = bucaramanga()
        repository.cachedForecast = sampleForecast()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val days = viewModel.uiState.value.days
        assertTrue(days.first().isToday)
        assertFalse(days[1].isToday)
        assertEquals("mar 7", days[1].dayLabel)
    }

    @Test
    fun `sin conexion se conserva el pronostico en cache`() = runTest {
        // Mismo contrato offline que la tarjeta principal (RF-03.2)
        repository.defaultCity = bucaramanga()
        repository.cachedForecast = sampleForecast()
        repository.refreshResult = Result.failure(IOException("sin red"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.days.size)
    }

    @Test
    fun `un refresco con exito actualiza el listado desde la cache`() = runTest {
        repository.defaultCity = bucaramanga()
        repository.cachedForecast = sampleForecast().take(3)
        repository.forecastAfterRefresh = sampleForecast()
        repository.refreshResult = Result.success(sampleSnapshot())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.days.size)
    }

    @Test
    fun `sin ciudad predeterminada no hay pronostico ni consulta de red`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.cityName)
        assertTrue(viewModel.uiState.value.days.isEmpty())
        assertEquals(0, repository.refreshCallCount)
    }

    private fun bucaramanga() = City(
        id = 3688465,
        name = "Bucaramanga",
        region = "Santander",
        country = "Colombia",
        latitude = 7.12539,
        longitude = -73.1198
    )

    private fun sampleForecast() = listOf(
        dayForecast(LocalDate.of(2026, 7, 6), WeatherCondition.PARTLY_CLOUDY, 55),
        dayForecast(LocalDate.of(2026, 7, 7), WeatherCondition.RAIN, 80),
        dayForecast(LocalDate.of(2026, 7, 8), WeatherCondition.RAIN, 70),
        dayForecast(LocalDate.of(2026, 7, 9), WeatherCondition.PARTLY_CLOUDY, 40),
        dayForecast(LocalDate.of(2026, 7, 10), WeatherCondition.CLEAR, 20)
    )

    private fun dayForecast(date: LocalDate, condition: WeatherCondition, rain: Int) = DailyForecast(
        date = date,
        condition = condition,
        maxTemperatureC = 28.1,
        minTemperatureC = 19.4,
        rainProbabilityPercent = rain
    )

    private fun sampleSnapshot() = WeatherSnapshot(
        cityName = "Bucaramanga",
        temperatureC = 24.3,
        condition = WeatherCondition.PARTLY_CLOUDY,
        maxTemperatureC = 28.1,
        minTemperatureC = 19.4,
        humidityPercent = 76,
        windSpeedKmh = 9.2,
        rainProbabilityPercent = 55,
        updatedAtEpochMillis = 1_783_500_000_000L
    )
}

private class FakeForecastWeatherRepository : WeatherRepository {
    var defaultCity: City? = null
    var cachedForecast: List<DailyForecast> = emptyList()
    var forecastAfterRefresh: List<DailyForecast>? = null
    var refreshResult: Result<WeatherSnapshot> = Result.failure(IOException("sin red"))
    var refreshCallCount = 0

    override suspend fun searchCities(query: String): Result<List<City>> = Result.success(emptyList())

    override suspend fun getDefaultCity(): City? = defaultCity

    override suspend fun saveDefaultCity(city: City) {
        defaultCity = city
    }

    override suspend fun getCachedSnapshot(): WeatherSnapshot? = null

    override suspend fun getCachedForecast(): List<DailyForecast> = cachedForecast

    override suspend fun refreshSnapshot(city: City): Result<WeatherSnapshot> {
        refreshCallCount++
        if (refreshResult.isSuccess) {
            forecastAfterRefresh?.let { cachedForecast = it }
        }
        return refreshResult
    }
}
