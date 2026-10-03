package com.auroraclima.app.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
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
 */
@HiltViewModel
class JournalListViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val loadEntryKinds: LoadEntryKindsUseCase,
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

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.entries()
                .onSuccess { entries ->
                    _uiState.update { it.copy(isLoading = false, entries = entries) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
                }
            attachmentRepository.counts()
                .onSuccess { counts -> _uiState.update { it.copy(attachmentCounts = counts) } }
        }
        viewModelScope.launch {
            // El catálogo llega por su lado (puede esperar a la red): la lista
            // no lo espera y se re-pinta cuando aparece.
            loadEntryKinds()
                .onSuccess { kinds -> _uiState.update { it.copy(kinds = kinds) } }
        }
    }
}
