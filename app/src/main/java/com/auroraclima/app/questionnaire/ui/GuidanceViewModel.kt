package com.auroraclima.app.questionnaire.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.core.directory.DirectoryProvider
import com.auroraclima.app.core.security.SessionManager
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.usecase.CalculateGuidanceLevelUseCase
import com.auroraclima.app.questionnaire.domain.usecase.SaveGuidanceOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flujo del cuestionario orientador validado (RF-27): avanzar, retroceder y
 * abandonar en cualquier momento, una pregunta por pantalla en dos bloques.
 *
 * Avanzar no exige responder: el motor de RF-29 trata la pregunta sin respuesta
 * como aporte 0 y `answeredCount` deja que la UI lo matice — no se obliga a
 * nadie a contestar una pregunta dura para continuar (nota de lenguaje cuidado
 * de RF-27).
 *
 * Al llegar al resultado se resuelven los accesos directos (§6) contra content
 * a través del contrato de core; el municipio elegido solo filtra en memoria.
 * Todo fallo se traduce a un mensaje genérico o se ignora en silencio: el
 * detalle del SDK jamás llega a la UI ni a un log (P-24, guía §3).
 */
@HiltViewModel
class GuidanceViewModel @Inject constructor(
    private val repository: QuestionnaireRepository,
    private val calculateGuidanceLevel: CalculateGuidanceLevelUseCase,
    private val saveGuidanceOutcome: SaveGuidanceOutcomeUseCase,
    private val directory: DirectoryProvider,
    sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuidanceUiState())
    val uiState: StateFlow<GuidanceUiState> = _uiState.asStateFlow()

    // Salir del cuestionario (v0.3.2). Consumo único: un replay tras rotar sacaría
    // de la pantalla sin gesto. Y una sola vez: un segundo pop sacaría el panel.
    private val _leaveEvents = Channel<Unit>(Channel.BUFFERED)
    val leaveEvents: Flow<Unit> = _leaveEvents.receiveAsFlow()
    private var hasLeft = false

    init {
        // RF-28: el descarte corre en la MISMA rutina de limpieza de RF-16/
        // RNF-03 — cualquier cierre de sesión (nube, inactividad, background
        // prolongado) borra respuestas y resultado en memoria, además del pop
        // del grafo que destruye este ViewModel. addCloseable da de baja el
        // registro cuando el ViewModel muere.
        addCloseable(sessionManager.registerCleaner { discardAnswers() })
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
        val state = _uiState.value
        val questionnaire = state.questionnaire ?: return
        if (state.phase != GuidancePhase.QUESTION) return
        if (state.isLastQuestion) {
            val result = calculateGuidanceLevel(questionnaire, state.answers)
            _uiState.update { it.copy(phase = GuidancePhase.RESULT, result = result) }
            resolveAccess(result.level)
        } else {
            _uiState.update { it.copy(questionIndex = it.questionIndex + 1) }
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

    /** Elegir el municipio filtra en memoria las entidades ya resueltas (§6). */
    fun onMunicipalitySelected(municipalityId: String) {
        _uiState.update { it.copy(selectedMunicipalityId = municipalityId) }
    }

    /**
     * «Terminar» (RF-31 criterio 1): abandono limpio sin preguntar (RF-27 criterio
     * 2). Las respuestas y el resultado se descartan por completo antes de salir, y
     * la salida pasa por el mismo `leave()` que las demás: una sola vez. El pop de la
     * ruta destruye además este ViewModel — la copia en memoria muere con él.
     */
    fun onAbandon() {
        leave()
    }

    /**
     * Flecha, gesto atrás, «Salir» o «Ahora no» (v0.3.2). Si se perdería algo, pregunta
     * antes; si no, sale. Salir siempre descarta todo antes (RF-27 criterio 2).
     * Mientras se guarda no hace nada: el guardado termina en la misma pantalla.
     */
    fun onLeaveRequested() {
        val state = _uiState.value
        when {
            state.isSaving -> Unit
            state.wouldLoseProgress -> _uiState.update { it.copy(isLeavePromptVisible = true) }
            else -> leave()
        }
    }

    /**
     * Gesto atrás del sistema: en una pregunta retrocede (RF-27 criterio 1), como la
     * flecha de abajo; en la portada y en el resultado hace lo mismo que la flecha
     * de arriba.
     */
    fun onBackRequested() {
        if (_uiState.value.phase == GuidancePhase.QUESTION) onPrevious() else onLeaveRequested()
    }

    fun onLeaveConfirmed() {
        _uiState.update { it.copy(isLeavePromptVisible = false) }
        leave()
    }

    fun onLeaveDismissed() {
        _uiState.update { it.copy(isLeavePromptVisible = false) }
    }

    private fun leave() {
        if (hasLeft) return
        hasLeft = true
        discardAnswers()
        _leaveEvents.trySend(Unit)
    }

    /** Abre la hoja de confirmación (RF-31 criterio 2). Tocar el botón aún no guarda nada. */
    fun onSaveRequested() {
        _uiState.update { state ->
            if (state.result == null || state.isSaved) state else state.copy(isSaveSheetVisible = true)
        }
    }

    fun onSaveDismissed() {
        _uiState.update { it.copy(isSaveSheetVisible = false, saveErrorRes = null) }
    }

    /**
     * Confirmación expresa del guardado (RF-31 criterio 2): recién aquí el
     * resultado toca disco — mínimo consultable, cifrado, vía el caso de uso.
     * Un fallo muestra el error genérico dentro de la hoja (§3) y no persiste.
     */
    fun onSaveConfirmed() {
        val result = _uiState.value.result ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveErrorRes = null) }
        viewModelScope.launch {
            saveGuidanceOutcome(result)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSaving = false, isSaveSheetVisible = false, isSaved = true)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSaving = false, saveErrorRes = R.string.common_error_generic)
                    }
                }
        }
    }

    /**
     * Resuelve todos los accesos posibles del nivel (líneas + entidades fijas +
     * entidades de todos los municipios) en una sola consulta; la selección de
     * municipio luego filtra en memoria. Un fallo se ignora en silencio (§3): el
     * resultado se muestra sin contactos y los consejos ya nombran las líneas.
     */
    private fun resolveAccess(level: GuidanceLevel) {
        val entityIds = (level.agencyIds + level.municipalAgencyIds.values.flatten()).distinct()
        viewModelScope.launch {
            directory.resolve(level.lineIds, entityIds)
                .onSuccess { access -> _uiState.update { it.copy(access = access) } }
        }
    }

    private fun discardAnswers() {
        // RF-28 cubre también la hoja de guardado abierta: si la salida rápida
        // llega antes de la confirmación expresa, no se persiste nada.
        _uiState.update {
            it.copy(
                phase = GuidancePhase.INTRO,
                questionIndex = 0,
                answers = emptyMap(),
                result = null,
                selectedMunicipalityId = null,
                access = null,
                isSaveSheetVisible = false,
                isSaving = false,
                isSaved = false,
                saveErrorRes = null,
                isLeavePromptVisible = false
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
