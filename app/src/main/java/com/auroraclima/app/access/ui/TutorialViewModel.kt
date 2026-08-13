package com.auroraclima.app.access.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.security.SearchGateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tutorial del espacio interno (RF-10). La barra de práctica evalúa lo tecleado con
 * la MISMA puerta que la fachada ([ValidateSecretCodeUseCase]): el ingreso de
 * práctica solo se da por logrado con la clave real de la usuaria, y lo tecleado
 * nunca se guarda ni se registra — vive en el estado mientras se escribe y muere
 * con el ViewModel al salir del grafo seguro.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val validateSecretCode: ValidateSecretCodeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    private var practiceCheckJob: Job? = null

    fun onNext() {
        _uiState.update { state ->
            val steps = TutorialStep.entries
            val next = steps.getOrNull(steps.indexOf(state.step) + 1) ?: state.step
            state.copy(step = next)
        }
    }

    fun onBack() {
        _uiState.update { state ->
            val steps = TutorialStep.entries
            val previous = steps.getOrNull(steps.indexOf(state.step) - 1) ?: state.step
            state.copy(step = previous)
        }
    }

    /**
     * Salto directo a la práctica: la vista V2 (v2-08) muestra los tres momentos
     * juntos y «Practicar» es su único avance. El orden de la máquina sigue
     * siendo de este ViewModel — la UI no conoce las páginas intermedias
     * (revisión de arquitectura S2, hallazgo M-1).
     */
    fun onStartPractice() {
        _uiState.update { it.copy(step = TutorialStep.PRACTICE) }
    }

    /**
     * Evaluación en vivo, como la barra real: cada tecla cuenta y solo la última
     * importa. Una vez logrado el ingreso queda fijo (criterio 2 de RF-10): la
     * usuaria ya practicó, editar el texto no le quita el logro.
     */
    fun onPracticeInputChange(value: String) {
        val current = _uiState.value
        if (current.practiceStatus == PracticeStatus.MATCHED) {
            _uiState.update { it.copy(practiceInput = value) }
            return
        }
        _uiState.update { it.copy(practiceInput = value, practiceStatus = PracticeStatus.TYPING) }
        practiceCheckJob?.cancel()
        if (value.trim().length < ValidateSecretCodeUseCase.MIN_CODE_LENGTH) return
        practiceCheckJob = viewModelScope.launch {
            val status = when (validateSecretCode.inspect(value)) {
                SearchGateResult.OpenAccess -> PracticeStatus.MATCHED
                // OpenActivation no ocurre con clave de usuaria creada (RF-08 la
                // reemplaza); si ocurriera, tampoco es un ingreso de práctica válido.
                else -> PracticeStatus.MISMATCH
            }
            _uiState.update { it.copy(practiceStatus = status) }
        }
    }
}
