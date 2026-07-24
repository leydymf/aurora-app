package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import com.auroraclima.app.records.domain.usecase.UpdateJournalEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Creación (RF-32) y edición (RF-35) de un registro con sincronización cifrada
 * a la nube (RF-37). El modo lo decide el argumento de navegación: con id se
 * carga el registro y el guardado re-cifra y sobreescribe; sin id se crea.
 * Validar en línea sin perder lo diligenciado (criterio 2, decisión F7) y
 * guardar protegido (criterio 1). Un fallo del guardado local (incluida la
 * apertura de la BD cifrada, P-36) se muestra solo como mensaje genérico y el
 * formulario queda intacto para reintentar; un fallo de la sync es silencioso
 * (§3), lo absorbe el caso de uso y jamás llega aquí.
 *
 * Adjuntos (RF-33): en edición se asocian directo al registro; en creación se
 * importan EN PREPARACIÓN (owner 0) y el guardado los reclama con el id nuevo.
 * El init barre huérfanos — adjuntos de creaciones abandonadas (salida rápida,
 * muerte de proceso) desaparecen antes de empezar. Todo fallo de importación
 * es el mensaje genérico; el único específico es «máximo alcanzado».
 */
@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addJournalEntry: AddJournalEntryUseCase,
    private val updateJournalEntry: UpdateJournalEntryUseCase,
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository
) : ViewModel() {
    // El argumento llega como String de la ruta; uno malformado no debe lanzar
    // (§3, auditoría C3 H-1): se trata igual que un registro que ya no existe.
    private val isEditing: Boolean = savedStateHandle.contains(ENTRY_ID_ARG)
    private val entryId: Long? = savedStateHandle.get<String>(ENTRY_ID_ARG)?.toLongOrNull()

    /** Dueño de los adjuntos: el registro en edición, o «en preparación» al crear. */
    private val attachmentOwner: Long
        get() = entryId ?: AttachmentPolicy.STAGED_OWNER

    private val _uiState = MutableStateFlow(JournalEditorUiState(isEditing = isEditing))
    val uiState: StateFlow<JournalEditorUiState> = _uiState.asStateFlow()

    // replay = 1 por el mismo motivo que LoginViewModel: el evento no debe
    // perderse si se emite antes de que la UI se suscriba.
    private val _savedEvents = MutableSharedFlow<Unit>(replay = 1)
    val savedEvents: SharedFlow<Unit> = _savedEvents.asSharedFlow()

    // La captura se lanza desde la UI (ActivityResult) con el URI que prepara
    // el repositorio. Canal de CONSUMO ÚNICO y no un SharedFlow con replay
    // (auditoría E1 H-1): el ViewModel sobrevive a la recreación de la Activity
    // y un replay relanzaría la cámara solo — y re-armaría la ida-y-vuelta —
    // sin gesto de la usuaria, en cada rotación o cambio de configuración.
    private val _captureEvents = Channel<String>(Channel.BUFFERED)
    val captureEvents: Flow<String> = _captureEvents.receiveAsFlow()

    init {
        if (isEditing) loadForEdit()
        viewModelScope.launch {
            // Limpia adjuntos en preparación de una creación abandonada antes
            // de listar; su fallo no es visible (el barrido reintenta después).
            attachmentRepository.sweepOrphans()
            refreshAttachments()
        }
    }

    fun onRetryLoad() {
        if (isEditing) loadForEdit()
    }

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

    /** Imagen elegida en el selector del sistema (galería); null = canceló. */
    fun onImagePicked(uri: String?) {
        if (uri == null) return
        importAttachment { attachmentRepository.importImage(uri, attachmentOwner) }
    }

    /** Archivo existente elegido (PDF/audio); null = canceló. */
    fun onDocumentPicked(uri: String?) {
        if (uri == null) return
        importAttachment { attachmentRepository.importDocument(uri, attachmentOwner) }
    }

    /** Prepara el destino privado de la cámara y pide a la UI lanzar el intent. */
    fun onRequestCapture() {
        if (!guardCapacity()) return
        _uiState.update { it.copy(isImporting = true, mediaErrorRes = null) }
        viewModelScope.launch {
            attachmentRepository.beginCapture()
                .onSuccess { uri ->
                    _uiState.update { it.copy(isImporting = false) }
                    _captureEvents.send(uri)
                }
                .onFailure {
                    _uiState.update { it.copy(isImporting = false, mediaErrorRes = R.string.journal_media_error) }
                }
        }
    }

    /** Resultado del intent de cámara: importar la captura o descartar el temporal. */
    fun onCaptureResult(saved: Boolean) {
        if (!saved) {
            viewModelScope.launch { attachmentRepository.discardCapture() }
            return
        }
        importAttachment { attachmentRepository.importCapture(attachmentOwner) }
    }

    /** El lanzamiento del selector o la cámara falló (dispositivo sin esa app). */
    fun onLaunchFailed() {
        viewModelScope.launch { attachmentRepository.discardCapture() }
        _uiState.update { it.copy(isImporting = false, mediaErrorRes = R.string.journal_media_error) }
    }

    fun onRemoveAttachment(id: Long) {
        viewModelScope.launch {
            attachmentRepository.delete(id)
                .onSuccess { refreshAttachments() }
                .onFailure {
                    _uiState.update { it.copy(mediaErrorRes = R.string.journal_media_error) }
                }
        }
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
            save(
                occurredOn = state.occurredOn,
                kind = state.kind,
                detail = state.detail.trim(),
                attachmentIds = state.attachments.map { it.id }
            )
                .onSuccess { _savedEvents.emit(Unit) }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.common_error_generic) }
                }
        }
    }

    private suspend fun save(
        occurredOn: LocalDate,
        kind: EntryKind,
        detail: String,
        attachmentIds: List<Long>
    ): Result<Unit> = if (entryId != null) {
        // En edición los adjuntos ya pertenecen al registro: no hay reclamo.
        updateJournalEntry(JournalEntry(id = entryId, occurredOn = occurredOn, kind = kind, detail = detail))
    } else {
        addJournalEntry(
            NewJournalEntry(occurredOn = occurredOn, kind = kind, detail = detail),
            attachmentIds
        )
    }

    private fun importAttachment(import: suspend () -> Result<*>) {
        if (!guardCapacity()) return
        _uiState.update { it.copy(isImporting = true, mediaErrorRes = null) }
        viewModelScope.launch {
            import()
                .onSuccess {
                    _uiState.update { it.copy(isImporting = false) }
                    refreshAttachments()
                }
                .onFailure {
                    _uiState.update { it.copy(isImporting = false, mediaErrorRes = R.string.journal_media_error) }
                }
        }
    }

    private fun guardCapacity(): Boolean {
        val hasCapacity = AttachmentPolicy.hasCapacity(_uiState.value.attachments.size)
        if (!hasCapacity) {
            _uiState.update { it.copy(mediaErrorRes = R.string.journal_media_full) }
        }
        return hasCapacity
    }

    private suspend fun refreshAttachments() {
        // Un fallo de lectura deja la lista como estaba: los chips no son
        // críticos y el error genérico ya lo dan las operaciones que fallen.
        attachmentRepository.attachments(attachmentOwner)
            .onSuccess { attachments -> _uiState.update { it.copy(attachments = attachments) } }
    }

    /** Carga el registro a editar (RF-35); id malformado, ausente o BD fallida → error genérico (P-24/P-36). */
    private fun loadForEdit() {
        val id = entryId ?: return showLoadError()
        _uiState.update { it.copy(isLoading = true, loadErrorRes = null) }
        viewModelScope.launch {
            repository.entry(id)
                .onSuccess { entry ->
                    if (entry == null) {
                        showLoadError()
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                occurredOn = entry.occurredOn,
                                kind = entry.kind,
                                detail = entry.detail
                            )
                        }
                    }
                }
                .onFailure { showLoadError() }
        }
    }

    private fun showLoadError() {
        _uiState.update { it.copy(isLoading = false, loadErrorRes = R.string.common_error_generic) }
    }
}
