package com.auroraclima.app.weather.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class WeatherViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(
        WeatherUiState(headerDate = formatHeaderDate(LocalDate.now()))
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}

/** Formatea la fecha del encabezado como en el mockup P01: "lun 6 jul". */
internal fun formatHeaderDate(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es")))
    .replace(".", "")
