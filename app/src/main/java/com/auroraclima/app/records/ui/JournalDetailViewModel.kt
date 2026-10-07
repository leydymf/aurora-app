package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.usecase.DeleteJournalEntryUseCase
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombre del argumento de navegación del detalle (neutro: queda en el APK). */
const val ENTRY_ID_ARG = "entryId"

/**
 * Detalle de un registro (RF-34, mockup P20) con edición (RF-35, por
 * navegación al editor) y eliminación en dos pasos (RF-36, mockup P20·b).
 * [refresh] se invoca en cada entrada en composición: al volver del editor la
 * ruta reingresa y los cambios se ven sin plomería adicional (mismo patrón del
 * listado). Un id inexistente y un fallo de la BD cifrada se muestran con el
 * mismo mensaje genérico (§3, P-24/P-36). El catálogo de tipos (P-68) no es
 * crítico: sin él, el tipo se pinta con su instantánea y el visual neutro.
 */
@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val loadEntryKinds: LoadEntryKindsUseCase
) : ViewModel() {
    // El argumento llega como String de la ruta; uno malformado o ausente no
    // debe lanzar (§3, auditoría C3 H-1): se trata igual que un id inexistente.
    val entryId: Long? = savedStateHandle.get<String>(ENTRY_ID_ARG)?.toLongOrNull()

    private val _uiState = MutableStateFlow(JournalDetailUiState())
    val uiState: StateFlow<JournalDetailUiState> = _uiState.asStateFlow()

    // replay = 1 por el mismo motivo que el editor: el evento no debe perderse
    // si se emite antes de que la UI se suscriba.
    private val _deletedEvents = MutableSharedFlow<Unit>(replay = 1)
    val deletedEvents: SharedFlow<Unit> = _deletedEvents.asSharedFlow()

    fun refresh() {
        val id = entryId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.entry(id)
                .onSuccess { entry ->
                    _uiState.update {
                        if (entry != null) {
                            it.copy(isLoading = false, entry = entry)
                        } else {
                            it.copy(isLoading = false, errorRes = R.string.common_error_generic)
                        }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
                }
            // Los adjuntos no son críticos (RF-33): si su lectura falla, la
            // sección simplemente no se pinta — sin error adicional.
            attachmentRepository.attachments(id)
                .onSuccess { attachments -> _uiState.update { it.copy(attachments = attachments) } }
        }
        viewModelScope.launch {
            loadEntryKinds()
                .onSuccess { kinds -> _uiState.update { it.copy(kinds = kinds) } }
        }
    }

    /** Primer paso de la eliminación (RF-36 criterio 1): abrir la confirmación. */
    fun onRequestDelete() {
        _uiState.update { it.copy(isConfirmingDelete = true, deleteErrorRes = null) }
    }

    fun onDismissDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isConfirmingDelete = false, deleteErrorRes = null) }
    }

    /** Segundo paso: borrar local y propagar a la nube (RF-36 criterio 2). */
    fun onConfirmDelete() {
        val id = entryId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, deleteErrorRes = null) }
        viewModelScope.launch {
            deleteJournalEntry(id)
                .onSuccess { _deletedEvents.emit(Unit) }
                .onFailure {
                    _uiState.update {
                        it.copy(isDeleting = false, deleteErrorRes = R.string.common_error_generic)
                    }
                }
        }
    }
}
