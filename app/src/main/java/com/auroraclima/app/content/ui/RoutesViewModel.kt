package com.auroraclima.app.content.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Triada Salud–Protección–Justicia con sus entidades (RF-22 criterio 1). Fallos
 * siempre con mensaje genérico, nunca el detalle del SDK (P-24, guía §3).
 */
@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutesUiState())
    val uiState: StateFlow<RoutesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.getRoutes()
                .onSuccess { routes ->
                    _uiState.update { it.copy(isLoading = false, routes = routes) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.content_error_generic) }
                }
        }
    }
}
