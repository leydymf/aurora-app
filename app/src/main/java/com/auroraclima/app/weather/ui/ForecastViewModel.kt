package com.auroraclima.app.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del pronóstico extendido (RF-04). Muestra la caché de inmediato y
 * refresca en red a mejor esfuerzo: sin conexión se conservan los últimos
 * datos guardados, igual que la tarjeta principal (RF-03.2).
 */
@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForecastUiState())
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val city = repository.getDefaultCity() ?: return@launch
            _uiState.update { it.copy(cityName = city.name) }
            showCachedForecast()
            repository.refreshSnapshot(city).onSuccess { showCachedForecast() }
        }
    }

    private suspend fun showCachedForecast() {
        val today = LocalDate.now(clock)
        val days = repository.getCachedForecast().map { it.toDayUiState(today) }
        if (days.isNotEmpty()) {
            _uiState.update { it.copy(days = days) }
        }
    }
}
