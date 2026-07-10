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
 * Líneas de atención (RF-23). Lectura simple: usa el repositorio directo (guía §3).
 * Sin conexión, la caché persistente sirve lo ya visto (RF-26); en la primera
 * apertura sin red el fallo se traduce al mensaje genérico con reintento — la
 * excepción cruda del SDK jamás llega a la UI ni a un log (P-24).
 */
@HiltViewModel
class LinesViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LinesUiState())
    val uiState: StateFlow<LinesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.getContactLines()
                .onSuccess { lines ->
                    _uiState.update { it.copy(isLoading = false, lines = lines) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.content_error_generic) }
                }
        }
    }
}
