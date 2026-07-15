package com.auroraclima.app.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Creación de un registro (RF-32) con sincronización cifrada a la nube (RF-37).
 * Validar en línea sin perder lo diligenciado (criterio 2, decisión F7) y guardar
 * protegido (criterio 1). Un fallo del guardado local (incluida la apertura de la
 * BD cifrada, P-36) se muestra solo como mensaje genérico y el formulario queda
 * intacto para reintentar; un fallo de la sync es silencioso (§3), lo absorbe el
 * caso de uso y jamás llega aquí.
 */
@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    private val addJournalEntry: AddJournalEntryUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(JournalEditorUiState())
    val uiState: StateFlow<JournalEditorUiState> = _uiState.asStateFlow()

    // replay = 1 por el mismo motivo que LoginViewModel: el evento no debe
    // perderse si se emite antes de que la UI se suscriba.
    private val _savedEvents = MutableSharedFlow<Unit>(replay = 1)
    val savedEvents: SharedFlow<Unit> = _savedEvents.asSharedFlow()

    fun onOpenDatePicker() {
        _uiState.update { it.copy(isPickingDate = true) }
    }

    fun onDismissDatePicker() {
        _uiState.update { it.copy(isPickingDate = false) }
    }

    fun onDaySelected(day: LocalDate) {
        _uiState.update { it.copy(occurredOn = day, isPickingDate = false) }
    }

    fun onKindSelected(kind: EntryKind) {
        _uiState.update { it.copy(kind = kind, kindErrorRes = null) }
    }

    fun onDetailChange(text: String) {
        _uiState.update { it.copy(detail = text, detailErrorRes = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isSaving) return
        // RF-32 criterio 2: si falta un campo se señala en línea y lo escrito permanece.
        if (state.kind == null || state.detail.isBlank()) {
            _uiState.update {
                it.copy(
                    kindErrorRes = if (state.kind == null) R.string.journal_kind_missing else null,
                    detailErrorRes = if (state.detail.isBlank()) R.string.journal_detail_missing else null
                )
            }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveErrorRes = null) }
        viewModelScope.launch {
            addJournalEntry(
                NewJournalEntry(
                    occurredOn = state.occurredOn,
                    kind = state.kind,
                    detail = state.detail.trim()
                )
            )
                .onSuccess { _savedEvents.emit(Unit) }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.common_error_generic) }
                }
        }
    }
}
