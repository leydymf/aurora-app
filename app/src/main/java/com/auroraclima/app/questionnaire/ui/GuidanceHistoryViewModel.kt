package com.auroraclima.app.questionnaire.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import com.auroraclima.app.questionnaire.domain.usecase.DeleteSavedOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Listado consultable de resultados guardados (RF-31): lectura del almacén
 * cifrado, detalle en hoja segura y eliminación individual con doble
 * confirmación y propagación remota (patrón RF-36). Vive en el grafo seguro;
 * su estado muere con el pop (salida rápida, inactividad). Todo fallo se
 * traduce al mensaje genérico (§3, P-24).
 */
@HiltViewModel
class GuidanceHistoryViewModel @Inject constructor(
    private val repository: SavedOutcomeRepository,
    private val questionnaireRepository: QuestionnaireRepository,
    private val deleteSavedOutcome: DeleteSavedOutcomeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuidanceHistoryUiState())
    val uiState: StateFlow<GuidanceHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    fun onOutcomeSelected(outcomeId: Long) {
        _uiState.update { state ->
            state.copy(detail = state.outcomes.firstOrNull { it.id == outcomeId })
        }
    }

    fun onDetailDismissed() {
        _uiState.update {
            it.copy(detail = null, isConfirmingDelete = false, deleteErrorRes = null)
        }
    }

    /** Primer paso de la eliminación: abre el segundo paso, sin tocar nada aún. */
    fun onDeleteRequested() {
        _uiState.update { state ->
            if (state.detail == null) state else state.copy(isConfirmingDelete = true)
        }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(isConfirmingDelete = false, deleteErrorRes = null) }
    }

    /** Segundo paso (doble confirmación): recién aquí se borra, local primero. */
    fun onDeleteConfirmed() {
        val target = _uiState.value.detail ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, deleteErrorRes = null) }
        viewModelScope.launch {
            deleteSavedOutcome(target.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isDeleting = false,
                            isConfirmingDelete = false,
                            detail = null,
                            outcomes = state.outcomes.filterNot { it.id == target.id }
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isDeleting = false, deleteErrorRes = R.string.common_error_generic)
                    }
                }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.outcomes()
                .onSuccess { outcomes ->
                    _uiState.update { it.copy(isLoading = false, outcomes = outcomes) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
                }
            // El aviso (RF-30) es tolerante: si el documento no llega, el
            // listado se muestra igual y el fallo es silencioso (§3).
            questionnaireRepository.getQuestionnaire()
                .onSuccess { questionnaire ->
                    _uiState.update { it.copy(notice = questionnaire.notice) }
                }
        }
    }
}
