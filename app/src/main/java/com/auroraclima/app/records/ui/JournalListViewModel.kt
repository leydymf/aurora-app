package com.auroraclima.app.records.ui

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

/**
 * Listado del historial (RF-34). [refresh] se invoca en cada entrada en
 * composición de la pantalla: al volver del editor (RF-32) la ruta reingresa
 * a composición y el registro nuevo aparece sin plomería adicional.
 */
@HiltViewModel
class JournalListViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(JournalListUiState())
    val uiState: StateFlow<JournalListUiState> = _uiState.asStateFlow()

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
        }
    }
}
