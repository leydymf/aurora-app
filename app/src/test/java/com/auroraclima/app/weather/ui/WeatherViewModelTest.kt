package com.auroraclima.app.weather.ui

import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition
import com.auroraclima.app.weather.domain.model.WeatherSnapshot
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeWeatherRepository()

    // lunes 2026-07-06, 09:28 hora de Bogotá, como en el mockup P01
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-07-06T14:28:00Z"),
        ZoneId.of("America/Bogota")
    )

    private fun createViewModel() = WeatherViewModel(repository, fixedClock)

    @Test
    fun `estado inicial tiene busqueda vacia y fecha formateada`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value

        assertEquals("", state.searchQuery)
        assertEquals("lun 6 jul", state.headerDate)
        assertNull(state.weather)
    }

    @Test
    fun `la fecha del encabezado sigue el formato del mockup P01`() {
        // lunes 6 de julio de 2026, como en alta-fidelidad-03
        assertEquals("lun 6 jul", formatHeaderDate(LocalDate.of(2026, 7, 6)))
    }

    @Test
    fun `escribir una ciudad muestra coincidencias tras el debounce`() = runTest {
        // RF-02 criterio 1
        repository.searchResult = Result.success(listOf(bucaramanga()))
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange("Bucara")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(bucaramanga()), state.searchResults)
        assertTrue(state.isSearchActive)
        assertFalse(state.showNoResults)
    }

    @Test
    fun `menos de dos caracteres no consulta el servicio`() = runTest {
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange("B")
        advanceUntilIdle()

        assertEquals(0, repository.searchCallCount)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun `la escritura rapida genera una sola consulta por el debounce`() = runTest {
        repository.searchResult = Result.success(listOf(bucaramanga()))
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange("Buc")
        advanceTimeBy(WeatherViewModel.SEARCH_DEBOUNCE_MS / 2)
        viewModel.onSearchQueryChange("Bucara")
        advanceUntilIdle()

        assertEquals(1, repository.searchCallCount)
        assertEquals("Bucara", repository.lastQuery)
    }

    @Test
    fun `una busqueda sin coincidencias muestra el estado sin resultados`() = runTest {
        // Base del comportamiento indistinguible (RF-13)
        repository.searchResult = Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange("xyzabc")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showNoResults)
        assertTrue(state.searchResults.isEmpty())
    }

    @Test
    fun `un fallo de red en la busqueda se muestra como sin resultados`() = runTest {
        repository.searchResult = Result.failure(IOException("sin red"))
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange("Bucara")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showNoResults)
    }

    @Test
    fun `seleccionar una ciudad muestra su clima y la guarda como predeterminada`() = runTest {
        // RF-02 criterios 2 y 3
        repository.refreshResult = Result.success(sampleSnapshot())
        val viewModel = createViewModel()

        viewModel.onCitySelected(bucaramanga())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(bucaramanga(), repository.defaultCity)
        assertEquals("Bucaramanga", state.weather?.cityName)
        assertEquals("24°", state.weather?.temperature)
        assertEquals("", state.searchQuery)
        assertFalse(state.isSearchActive)
    }

    @Test
    fun `al reabrir con ciudad predeterminada se carga su clima`() = runTest {
        // RF-02 criterio 3, RF-03 criterio 1
        repository.defaultCity = bucaramanga()
        repository.refreshResult = Result.success(sampleSnapshot())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bucaramanga", state.weather?.cityName)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, state.weather?.condition)
    }

    @Test
    fun `sin conexion se muestran los ultimos datos con hora de actualizacion`() = runTest {
        // RF-03 criterio 2
        repository.defaultCity = bucaramanga()
        repository.cachedSnapshot = sampleSnapshot()
        repository.refreshResult = Result.failure(IOException("sin red"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val weather = viewModel.uiState.value.weather
        assertNotNull(weather)
        assertEquals("Bucaramanga", weather?.cityName)
        assertTrue(weather?.updatedAtTime.orEmpty().isNotBlank())
    }

    @Test
    fun `cancelar la busqueda limpia texto y resultados`() = runTest {
        repository.searchResult = Result.success(listOf(bucaramanga()))
        val viewModel = createViewModel()
        viewModel.onSearchQueryChange("Bucara")
        advanceUntilIdle()

        viewModel.onSearchCancel()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearchActive)
    }

    private fun bucaramanga() = City(
        id = 3688465,
        name = "Bucaramanga",
        region = "Santander",
        country = "Colombia",
        latitude = 7.12539,
        longitude = -73.1198
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

private class FakeWeatherRepository : WeatherRepository {
    var searchResult: Result<List<City>> = Result.success(emptyList())
    var searchCallCount = 0
    var lastQuery: String? = null
    var defaultCity: City? = null
    var cachedSnapshot: WeatherSnapshot? = null
    var refreshResult: Result<WeatherSnapshot> = Result.failure(IOException("sin red"))

    override suspend fun searchCities(query: String): Result<List<City>> {
        searchCallCount++
        lastQuery = query
        return searchResult
    }

    override suspend fun getDefaultCity(): City? = defaultCity

    override suspend fun saveDefaultCity(city: City) {
        defaultCity = city
    }

    override suspend fun getCachedSnapshot(): WeatherSnapshot? = cachedSnapshot

    override suspend fun refreshSnapshot(city: City): Result<WeatherSnapshot> = refreshResult
}
