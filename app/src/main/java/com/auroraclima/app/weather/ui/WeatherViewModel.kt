package com.auroraclima.app.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SearchQueryGate
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel único de la fachada de clima (RF-01, RF-02, RF-03).
 * Centraliza TODO el texto de la barra de búsqueda: esa barra es también la
 * puerta del código secreto (RF-12) a través de [SearchQueryGate], así que el
 * manejo del texto no debe duplicarse en ningún otro componente.
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val searchQueryGate: SearchQueryGate,
    clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WeatherUiState(headerDate = formatHeaderDate(LocalDate.now(clock)))
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _gateEvents = MutableSharedFlow<SearchGateResult>(extraBufferCapacity = 1)

    /**
     * Aperturas del espacio interno (RF-05, RF-12); las consume solo la navegación raíz.
     * Sin replay: si no hay colector en el instante de la emisión (p. ej. recreación de
     * la Activity) el evento se pierde, y esa es la dirección segura del fallo — nunca
     * se abre de más; la usuaria simplemente vuelve a teclear el código.
     */
    val gateEvents: SharedFlow<SearchGateResult> = _gateEvents.asSharedFlow()

    private var searchJob: Job? = null
    private var refreshJob: Job? = null

    init {
        // Dueño único del refresco: elegir otra ciudad o reintentar lo cancela y el
        // refresco inicial no puede pisar después a la ciudad elegida.
        refreshJob = viewModelScope.launch {
            val city = repository.getDefaultCity() ?: return@launch
            repository.getCachedSnapshot()?.let { cached ->
                _uiState.update { it.copy(weather = cached.toCardUiState()) }
            }
            refreshWeather(city, fallbackToCache = true)
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        val searchable = trimmed.length >= MIN_SEARCH_LENGTH
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = query.isNotEmpty(),
                showNoResults = false,
                searchFailed = false,
                // El indicador arranca con la tecla, sin esperar al debounce, y es el
                // mismo para cualquier texto: un código no se distingue de una ciudad.
                isSearching = searchable,
                searchResults = if (searchable) it.searchResults else emptyList()
            )
        }
        if (!searchable) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(trimmed)
        }
    }

    /** Reintento tras un fallo de la búsqueda: sin debounce, sobre el texto que sigue en la barra. */
    fun onRetrySearch() {
        val trimmed = _uiState.value.searchQuery.trim()
        if (trimmed.length < MIN_SEARCH_LENGTH) return
        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = true, searchFailed = false, showNoResults = false) }
        searchJob = viewModelScope.launch { runSearch(trimmed) }
    }

    private suspend fun runSearch(query: String) {
        // La puerta corre en paralelo con la búsqueda real y la respuesta visible
        // espera SIEMPRE a la búsqueda (o a su plazo): un código erróneo es, en UI,
        // mensajes, tiempos y tráfico de red, exactamente una búsqueda de ciudad, y un
        // código válido no responde más rápido que una búsqueda (RF-13).
        val (destination, results) = coroutineScope {
            val gateResult = async { searchQueryGate.inspect(query) }
            val found = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { repository.searchCities(query).getOrNull() }
            gateResult.await() to found
        }
        when (destination) {
            SearchGateResult.PassThrough -> _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results.orEmpty(),
                    showNoResults = results != null && results.isEmpty(),
                    searchFailed = results == null
                )
            }
            else -> {
                // RF-14: la barra queda vacía antes de abrir; el código no queda
                // en el estado ni viaja en el evento. Abre aunque la búsqueda haya
                // fallado: sin red el acceso sigue funcionando.
                clearSearch()
                _gateEvents.tryEmit(destination)
            }
        }
    }

    fun onCitySelected(city: City) {
        clearSearch()
        // La ciudad anterior no se queda a la vista mientras llega la nueva.
        _uiState.update { it.copy(weather = null, isLoadingWeather = true, weatherFailed = false) }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repository.saveDefaultCity(city)
            refreshWeather(city, fallbackToCache = false)
        }
    }

    /** Reintento del clima tras un fallo: sobre la ciudad predeterminada ya guardada. */
    fun onRetryWeather() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isLoadingWeather = true, weatherFailed = false) }
        refreshJob = viewModelScope.launch {
            val city = repository.getDefaultCity()
            if (city == null) {
                _uiState.update { it.copy(isLoadingWeather = false) }
            } else {
                refreshWeather(city, fallbackToCache = true)
            }
        }
    }

    fun onSearchCancel() {
        clearSearch()
    }

    private fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchActive = false,
                searchResults = emptyList(),
                showNoResults = false,
                isSearching = false,
                searchFailed = false
            )
        }
    }

    /**
     * [fallbackToCache]: con la ciudad predeterminada y sin red se muestran los
     * últimos datos con su hora (RF-03.2); al elegir una ciudad nueva no, porque
     * lo guardado es de otra ciudad y se vería como si fuera la elegida.
     */
    private suspend fun refreshWeather(city: City, fallbackToCache: Boolean) {
        val shownBefore = _uiState.value.weather != null
        if (!shownBefore) _uiState.update { it.copy(isLoadingWeather = true, weatherFailed = false) }
        val refreshed = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { repository.refreshSnapshot(city).getOrNull() }
        if (refreshed != null) {
            _uiState.update {
                it.copy(weather = refreshed.toCardUiState(), isLoadingWeather = false, weatherFailed = false)
            }
            return
        }
        val cached = if (fallbackToCache && _uiState.value.weather == null) repository.getCachedSnapshot() else null
        _uiState.update {
            it.copy(
                weather = cached?.toCardUiState() ?: it.weather,
                isLoadingWeather = false,
                weatherFailed = cached == null && it.weather == null
            )
        }
    }

    companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L

        /** Plazo de cada llamada de red de la fachada: pasado, se muestra el error con reintento. */
        const val NETWORK_TIMEOUT_MS = 10_000L
    }
}

/** Formatea la fecha del encabezado como en el mockup P01: "lun 6 jul". */
internal fun formatHeaderDate(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es")))
    .replace(".", "")
