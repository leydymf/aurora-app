package com.auroraclima.app.questionnaire.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.usecase.CalculateGuidanceLevelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flujo del cuestionario orientador (RF-27): avanzar, retroceder y abandonar
 * en cualquier momento, una pregunta por pantalla (decisión F6 de A2-6).
 *
 * Avanzar no exige responder: el motor de RF-29 trata la pregunta sin
 * respuesta como aporte 0 y `answeredCount` deja que la UI lo matice — no se
 * obliga a nadie a contestar una pregunta dura para continuar (nota de
 * lenguaje cuidado de RF-27).
 *
 * Todo fallo del repositorio se traduce a un mensaje genérico: el detalle del
 * SDK jamás llega a la UI ni a un log (P-24, guía §3).
 */
@HiltViewModel
class GuidanceViewModel @Inject constructor(
    private val repository: QuestionnaireRepository,
    private val calculateGuidanceLevel: CalculateGuidanceLevelUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuidanceUiState())
    val uiState: StateFlow<GuidanceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    fun onStart() {
        _uiState.update { state ->
            if (state.questionnaire == null) state else state.copy(phase = GuidancePhase.QUESTION)
        }
    }

    fun onOptionSelected(optionId: String) {
        _uiState.update { state ->
            val question = state.currentQuestion ?: return@update state
            state.copy(answers = state.answers + (question.id to optionId))
        }
    }

    fun onNext() {
        _uiState.update { state ->
            val questionnaire = state.questionnaire
            when {
                questionnaire == null || state.phase != GuidancePhase.QUESTION -> state
                state.isLastQuestion -> state.copy(
                    phase = GuidancePhase.RESULT,
                    result = calculateGuidanceLevel(questionnaire, state.answers)
                )
                else -> state.copy(questionIndex = state.questionIndex + 1)
            }
        }
    }

    /** Retroceder conserva la respuesta ya elegida; desde la primera pregunta vuelve a la portada. */
    fun onPrevious() {
        _uiState.update { state ->
            when {
                state.phase != GuidancePhase.QUESTION -> state
                state.questionIndex == 0 -> state.copy(phase = GuidancePhase.INTRO)
                else -> state.copy(questionIndex = state.questionIndex - 1)
            }
        }
    }

    /**
     * Abandono limpio (RF-27 criterio 2): las respuestas y el resultado se
     * descartan por completo antes de salir. El pop de la ruta destruye además
     * este ViewModel — la copia en memoria muere con él.
     */
    fun onAbandon() {
        discardAnswers()
    }

    private fun discardAnswers() {
        _uiState.update {
            it.copy(
                phase = GuidancePhase.INTRO,
                questionIndex = 0,
                answers = emptyMap(),
                result = null
            )
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.getQuestionnaire()
                .onSuccess { questionnaire ->
                    _uiState.update { it.copy(isLoading = false, questionnaire = questionnaire) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
                }
        }
    }
}
