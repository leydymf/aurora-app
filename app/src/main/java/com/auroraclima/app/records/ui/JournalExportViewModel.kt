package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.DayProvider
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.repository.JournalExportRepository
import com.auroraclima.app.records.domain.usecase.BuildExportDocumentUseCase
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
import com.auroraclima.app.records.domain.usecase.LoadExportSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ENTRY_IDS_ARG = "entryIds"

/**
 * Exportación selectiva (RF-45). Recibe por la ruta SOLO los ids elegidos
 * («1,2,3»), vuelve a leer el resumen y, al exportar, arma el documento con
 * el nombre vivo de cada tipo (P-68) y lo entrega:
 * - «Guardar»: pide al sistema crear el documento ([ExportLaunch.Save]) y,
 *   con el URI de vuelta, escribe el PDF directo allí — sin temporal. Si la
 *   usuaria cancela el selector no se genera nada.
 * - «Compartir»: escribe el PDF cifrado a un temporal privado y pide el
 *   selector de apps ([ExportLaunch.Share]); al volver, el temporal se borra.
 *
 * Los lanzamientos viajan por un [Channel] de consumo único (auditoría E1
 * H-1): el ViewModel sobrevive a la recreación de la Activity y un replay
 * relanzaría el selector sin gesto de la usuaria. La contraseña vive en el
 * estado solo mientras se exporta y se vacía al terminar; nada de ella ni
 * del contenido pasa por logs (regla 4). Al destruirse el ViewModel (pop del
 * grafo por salida rápida o inactividad) el temporal también se barre.
 */
@HiltViewModel
class JournalExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadSummary: LoadExportSummaryUseCase,
    private val loadEntryKinds: LoadEntryKindsUseCase,
    private val buildDocument: BuildExportDocumentUseCase,
    private val exportRepository: JournalExportRepository,
    private val dayProvider: DayProvider
) : ViewModel() {
    private val entryIds: List<Long> =
        savedStateHandle.get<String>(ENTRY_IDS_ARG).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    private val _uiState = MutableStateFlow(JournalExportUiState())
    val uiState: StateFlow<JournalExportUiState> = _uiState.asStateFlow()

    private val _launchEvents = Channel<ExportLaunch>(Channel.BUFFERED)
    val launchEvents: Flow<ExportLaunch> = _launchEvents.receiveAsFlow()

    init {
        viewModelScope.launch { exportRepository.clearStaged() }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            loadSummary(entryIds)
                .onSuccess { summary -> _uiState.update { it.copy(isLoading = false, summary = summary) } }
                .onFailure { _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) } }
        }
        viewModelScope.launch {
            loadEntryKinds().onSuccess { kinds -> _uiState.update { it.copy(kinds = kinds) } }
        }
    }

    fun onIncludeAttachmentsChanged(include: Boolean) {
        _uiState.update { state ->
            val allowed = state.summary?.canIncludeAttachments == true
            state.copy(includeAttachments = include && allowed)
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorRes = null) }
    }

    fun onConfirmationChanged(value: String) {
        _uiState.update { it.copy(confirmation = value, errorRes = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /** «Guardar en el teléfono»: valida y pide el selector; el PDF se escribe cuando vuelva el URI. */
    fun onSaveRequested() {
        if (!validate()) return
        viewModelScope.launch { _launchEvents.send(ExportLaunch.Save(ExportPolicy.fileName(dayProvider.today()))) }
    }

    /** Resultado de «crear documento»: null = la usuaria canceló y no se genera nada. */
    fun onSaveTarget(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val delivered = export { document, password -> exportRepository.exportTo(uri, document, password) }
            if (delivered) {
                _uiState.update {
                    it.copy(isSaved = true, password = "", confirmation = "", showsPasswordError = false)
                }
            } else {
                // El selector ya creó el documento: si no llegó a escribirse,
                // no debe quedar vacío en la carpeta elegida (auditoría H-3).
                exportRepository.discard(uri)
            }
        }
    }

    /** «Compartir»: valida, escribe el temporal cifrado y pide el selector de apps. */
    fun onShareRequested() {
        if (!validate()) return
        viewModelScope.launch {
            export { document, password ->
                exportRepository.stage(document, password)
                    .onSuccess { uri -> _launchEvents.send(ExportLaunch.Share(uri)) }
                    .map { }
            }
        }
    }

    /** De vuelta del selector de apps (cualquier resultado): ni el temporal ni la contraseña sobreviven. */
    fun onShareReturned() {
        _uiState.update { it.copy(password = "", confirmation = "", showsPasswordError = false) }
        viewModelScope.launch { exportRepository.clearStaged() }
    }

    /** El sistema no pudo abrir el selector: mensaje genérico y sin temporal. */
    fun onLaunchFailed() {
        _uiState.update { it.copy(isExporting = false, errorRes = R.string.common_error_generic) }
        viewModelScope.launch { exportRepository.clearStaged() }
    }

    override fun onCleared() {
        // La contraseña no sobrevive al ViewModel; el temporal lo barre el
        // cleaner de sesión (pop del grafo) y la próxima apertura (init).
        _uiState.update { it.copy(password = "", confirmation = "") }
        super.onCleared()
    }

    private fun validate(): Boolean {
        val valid = _uiState.value.isPasswordValid
        _uiState.update { it.copy(showsPasswordError = !valid) }
        return valid
    }

    /** Arma el documento y lo entrega; true si todo salió bien. Fallo → mensaje genérico. */
    private suspend fun export(deliver: suspend (ExportDocument, String) -> Result<Unit>): Boolean {
        val state = _uiState.value
        _uiState.update { it.copy(isExporting = true, errorRes = null) }
        // Los adjuntos se descifran a memoria: un OutOfMemoryError (Error, no
        // Exception) en gama baja debe terminar en el mensaje genérico, jamás
        // en la caída del proceso dentro del espacio interno (auditoría A-1).
        val outcome = try {
            buildDocument(entryIds, state.includeAttachments, dayProvider.today())
                .fold(onSuccess = { document -> deliver(document, state.password) }, onFailure = { Result.failure(it) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
        _uiState.update {
            it.copy(isExporting = false, errorRes = if (outcome.isSuccess) null else R.string.common_error_generic)
        }
        return outcome.isSuccess
    }
}
