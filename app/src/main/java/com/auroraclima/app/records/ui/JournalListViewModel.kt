package com.auroraclima.app.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.core.security.CloudReconciliation
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Listado del historial (RF-34). [refresh] se invoca en cada entrada en
 * composición de la pantalla: al volver del editor (RF-32) la ruta reingresa
 * a composición y el registro nuevo aparece sin plomería adicional. Los
 * contadores de adjuntos (RF-33, P18) y el catálogo de tipos (P-68) no son
 * críticos: si fallan, las filas se pintan sin contador o con la instantánea
 * del nombre y el visual neutro, sin error propio.
 *
 * RF-45: el modo de selección vive aquí (la pestaña sobrevive al cambio de
 * pestaña, el grafo seguro lo mata con el pop). [refresh] conserva las marcas
 * pero poda los ids que ya no existen (un registro borrado en el detalle).
 */
@HiltViewModel
class JournalListViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val loadEntryKinds: LoadEntryKindsUseCase,
    private val cloudReconciliation: CloudReconciliation,
    keyManager: SyncKeyManager
) : ViewModel() {
    // El aviso de respaldo reiniciado (RF-39) se consume UNA vez y vive en el
    // estado hasta que la usuaria lo cierre; si el proceso muere antes de
    // verse, se pierde — es informativo, los datos nuevos no dependen de él.
    private val _uiState = MutableStateFlow(
        JournalListUiState(showsBackupNotice = keyManager.consumeRestartNotice())
    )
    val uiState: StateFlow<JournalListUiState> = _uiState.asStateFlow()

    fun onBackupNoticeDismissed() {
        _uiState.update { it.copy(showsBackupNotice = false) }
    }

    fun enterSelection() {
        _uiState.update { it.copy(isSelecting = true, selectedIds = emptySet()) }
    }

    fun toggleSelected(entryId: Long) {
        _uiState.update { state ->
            val ids = if (entryId in state.selectedIds) state.selectedIds - entryId else state.selectedIds + entryId
            state.copy(selectedIds = ids)
        }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }
    }

    /**
     * Ids marcados en el orden del listado (los más recientes primero) y salida
     * del modo: la pantalla de exportación recibe solo ids por la ruta.
     */
    fun continueSelection(): List<Long> {
        val state = _uiState.value
        val ordered = state.entries.map { it.id }.filter { it in state.selectedIds }
        _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }
        return ordered
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            loadEntries(showErrors = true)
            loadCounts()
        }
        viewModelScope.launch {
            // El catálogo llega por su lado (puede esperar a la red): la lista
            // no lo espera y se re-pinta cuando aparece.
            loadEntryKinds()
                .onSuccess { kinds -> _uiState.update { it.copy(kinds = kinds) } }
        }
        viewModelScope.launch {
            // Lo que se editó o eliminó en el otro dispositivo (ADR 2026-10-06)
            // llega por su lado: la lista ya se pintó con lo local y se vuelve a
            // leer cuando la reconciliación termina. Sin red no hace nada.
            cloudReconciliation.reconcile()
            loadEntries(showErrors = false)
            loadCounts()
        }
    }

    /**
     * Lee el historial y poda las marcas de selección de lo que ya no existe.
     * La relectura posterior a la reconciliación NO muestra errores: la lista
     * ya está pintada y un fallo de fondo no debe estropearla (§3).
     */
    private suspend fun loadEntries(showErrors: Boolean) {
        repository.entries()
            .onSuccess { entries ->
                val surviving = entries.map { it.id }.toSet()
                _uiState.update {
                    it.copy(isLoading = false, entries = entries, selectedIds = it.selectedIds intersect surviving)
                }
            }
            .onFailure {
                if (showErrors) {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
                }
            }
    }

    private suspend fun loadCounts() {
        attachmentRepository.counts()
            .onSuccess { counts -> _uiState.update { it.copy(attachmentCounts = counts) } }
    }
}
