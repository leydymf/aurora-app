package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombre del argumento de navegación del detalle (neutro: queda en el APK). */
const val ENTRY_ID_ARG = "entryId"

/**
 * Detalle de un registro (RF-34, mockup P20). Un id inexistente y un fallo de
 * la BD cifrada se muestran con el mismo mensaje genérico (§3, P-24/P-36).
 */
@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository
) : ViewModel() {
    private val entryId: Long = requireNotNull(savedStateHandle.get<String>(ENTRY_ID_ARG)) {
        "Falta el argumento de navegación $ENTRY_ID_ARG"
    }.toLong()

    private val _uiState = MutableStateFlow(JournalDetailUiState())
    val uiState: StateFlow<JournalDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.entry(entryId)
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
        }
    }
}
