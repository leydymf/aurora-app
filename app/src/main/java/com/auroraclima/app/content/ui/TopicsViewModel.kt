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
 * Listado de tipos (RF-21). Lectura simple: usa el repositorio directo (guía §3).
 * Todo fallo se traduce a un mensaje genérico — la excepción cruda del SDK revela
 * el proyecto y las rutas de colección y jamás llega a la UI ni a un log (P-24).
 */
@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicsUiState())
    val uiState: StateFlow<TopicsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.getTopics()
                .onSuccess { topics ->
                    _uiState.update { it.copy(isLoading = false, topics = topics) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.content_error_generic) }
                }
        }
    }
}
