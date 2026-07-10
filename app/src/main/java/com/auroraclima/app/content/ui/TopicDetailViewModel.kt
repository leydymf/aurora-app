package com.auroraclima.app.content.ui

import androidx.lifecycle.SavedStateHandle
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

/** Nombre del argumento de navegación del detalle de tipo (neutro: queda en el APK). */
const val TOPIC_ID_ARG = "topicId"

/**
 * Detalle de un tipo (RF-21): descripción, señales y ejemplos. La lectura reutiliza
 * la consulta del listado — la caché local de la fuente de datos la resuelve sin red
 * (RF-26) — y un id inexistente o un fallo se muestran con el mismo mensaje genérico (P-24).
 */
@HiltViewModel
class TopicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ContentRepository
) : ViewModel() {
    private val topicId: String = requireNotNull(savedStateHandle[TOPIC_ID_ARG]) {
        "Falta el argumento de navegación $TOPIC_ID_ARG"
    }

    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()

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
                    val topic = topics.firstOrNull { it.id == topicId }
                    _uiState.update {
                        if (topic != null) {
                            it.copy(isLoading = false, topic = topic)
                        } else {
                            it.copy(isLoading = false, errorRes = R.string.content_error_generic)
                        }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.content_error_generic) }
                }
        }
    }
}
