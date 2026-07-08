package com.auroraclima.app.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel único de la fachada de clima (RF-01, RF-02, RF-03).
 * Centraliza TODO el texto de la barra de búsqueda: esa barra será también la
 * puerta del código secreto (RF-12), así que el manejo del texto no debe
 * duplicarse en ningún otro componente.
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WeatherUiState(headerDate = formatHeaderDate(LocalDate.now(clock)))
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val city = repository.getDefaultCity() ?: return@launch
            repository.getCachedSnapshot()?.let { cached ->
                _uiState.update { it.copy(weather = cached.toCardUiState()) }
            }
            refreshWeather(city)
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = query.isNotEmpty(),
                showNoResults = false,
                searchResults = if (trimmed.length < MIN_SEARCH_LENGTH) emptyList() else it.searchResults
            )
        }
        if (trimmed.length < MIN_SEARCH_LENGTH) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = repository.searchCities(trimmed).getOrDefault(emptyList())
            _uiState.update {
                it.copy(searchResults = results, showNoResults = results.isEmpty())
            }
        }
    }

    fun onCitySelected(city: City) {
        clearSearch()
        viewModelScope.launch {
            repository.saveDefaultCity(city)
            refreshWeather(city)
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
                showNoResults = false
            )
        }
    }

    private suspend fun refreshWeather(city: City) {
        repository.refreshSnapshot(city)
            .onSuccess { snapshot ->
                _uiState.update { it.copy(weather = snapshot.toCardUiState()) }
            }
            .onFailure {
                // Sin conexión: se muestran los últimos datos guardados con su hora (RF-03.2).
                if (_uiState.value.weather == null) {
                    repository.getCachedSnapshot()?.let { cached ->
                        _uiState.update { it.copy(weather = cached.toCardUiState()) }
                    }
                }
            }
    }

    companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/** Formatea la fecha del encabezado como en el mockup P01: "lun 6 jul". */
internal fun formatHeaderDate(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es")))
    .replace(".", "")
