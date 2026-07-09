package com.auroraclima.app.weather.ui

import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SearchQueryGate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val gate = FakeSearchQueryGate()

    // lunes 2026-07-06, 09:28 hora de Bogotá, como en el mockup P01
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-07-06T14:28:00Z"),
        ZoneId.of("America/Bogota")
    )

    private fun createViewModel() = WeatherViewModel(repository, gate, fixedClock)

    /** Registra en una lista los eventos de apertura emitidos por el ViewModel. */
    private fun TestScope.collectGateEvents(viewModel: WeatherViewModel): List<SearchGateResult> {
        val events = mutableListOf<SearchGateResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.gateEvents.toList(events)
        }
        return events
    }

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

    @Test
    fun `un codigo erroneo se comporta exactamente como una busqueda fallida`() = runTest {
        // RF-13 criterio 2: misma UI, mismo estado, ningún evento
        repository.searchResult = Result.success(emptyList())
        gate.result = SearchGateResult.PassThrough
        val viewModel = createViewModel()
        val events = collectGateEvents(viewModel)

        viewModel.onSearchQueryChange("codigofalso")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showNoResults)
        assertTrue(state.searchResults.isEmpty())
        assertEquals("codigofalso", state.searchQuery)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `el codigo secreto abre el doble fondo y deja la barra vacia`() = runTest {
        // RF-12 criterio 1 y RF-14 criterio 1
        repository.searchResult = Result.success(emptyList())
        gate.result = SearchGateResult.OpenAccess
        val viewModel = createViewModel()
        val events = collectGateEvents(viewModel)

        viewModel.onSearchQueryChange("cielo2026")
        advanceUntilIdle()

        assertEquals(listOf<SearchGateResult>(SearchGateResult.OpenAccess), events)
        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertFalse(state.isSearchActive)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.showNoResults)
    }

    @Test
    fun `el codigo de activacion abre el registro`() = runTest {
        // RF-05 criterio 1
        repository.searchResult = Result.success(emptyList())
        gate.result = SearchGateResult.OpenActivation
        val viewModel = createViewModel()
        val events = collectGateEvents(viewModel)

        viewModel.onSearchQueryChange("luna2026")
        advanceUntilIdle()

        assertEquals(listOf<SearchGateResult>(SearchGateResult.OpenActivation), events)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `la apertura nunca responde antes que la busqueda real`() = runTest {
        // RF-13: la validación no es observablemente más rápida que una búsqueda;
        // aunque la puerta ya haya resuelto, nada pasa hasta que searchCities termina
        repository.searchResult = Result.success(emptyList())
        repository.searchDelayMs = 500
        gate.result = SearchGateResult.OpenAccess
        val viewModel = createViewModel()
        val events = collectGateEvents(viewModel)

        viewModel.onSearchQueryChange("cielo2026")
        advanceTimeBy(WeatherViewModel.SEARCH_DEBOUNCE_MS + 100)

        // La búsqueda sigue en curso: sin evento y con la barra intacta
        assertTrue(events.isEmpty())
        assertEquals("cielo2026", viewModel.uiState.value.searchQuery)

        advanceUntilIdle()
        assertEquals(listOf<SearchGateResult>(SearchGateResult.OpenAccess), events)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `la puerta recibe el texto recortado de la barra`() = runTest {
        repository.searchResult = Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.onSearchQueryChange(" cielo2026 ")
        advanceUntilIdle()

        assertEquals("cielo2026", gate.lastQuery)
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

private class FakeSearchQueryGate : SearchQueryGate {
    var result: SearchGateResult = SearchGateResult.PassThrough
    var lastQuery: String? = null

    override suspend fun inspect(query: String): SearchGateResult {
        lastQuery = query
        return result
    }
}

private class FakeWeatherRepository : WeatherRepository {
    var searchResult: Result<List<City>> = Result.success(emptyList())
    var searchCallCount = 0
    var searchDelayMs = 0L
    var lastQuery: String? = null
    var defaultCity: City? = null
    var cachedSnapshot: WeatherSnapshot? = null
    var refreshResult: Result<WeatherSnapshot> = Result.failure(IOException("sin red"))

    override suspend fun searchCities(query: String): Result<List<City>> {
        searchCallCount++
        lastQuery = query
        if (searchDelayMs > 0) delay(searchDelayMs)
        return searchResult
    }

    override suspend fun getDefaultCity(): City? = defaultCity

    override suspend fun saveDefaultCity(city: City) {
        defaultCity = city
    }

    override suspend fun getCachedSnapshot(): WeatherSnapshot? = cachedSnapshot

    override suspend fun refreshSnapshot(city: City): Result<WeatherSnapshot> = refreshResult
}
