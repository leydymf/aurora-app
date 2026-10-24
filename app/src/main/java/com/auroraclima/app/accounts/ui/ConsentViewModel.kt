package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.accounts.domain.usecase.DiscardUnfinishedActivationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Consentimiento informado (RF-17, RF-18). Mientras la usuaria no acepte y cree
 * su cuenta, esta pantalla no recolecta ni persiste dato alguno (RF-18 por
 * construcción): la aceptación solo habilita continuar y no se guarda.
 *
 * Su única dependencia BORRA, no recolecta: el consentimiento es la única
 * entrada del flujo de activación (la puerta de búsqueda lo abre con
 * `OpenActivation`), así que aquí se descarta lo que haya dejado un registro
 * sin terminar (hallazgo A de v0.3.2). Se lanza sin esperarlo; el login y la
 * creación de cuenta lo vuelven a pedir antes de hablar con el proveedor.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor(
    discardUnfinished: DiscardUnfinishedActivationUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsentUiState())
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { discardUnfinished() }
    }

    fun onAcceptanceChange(isAccepted: Boolean) {
        _uiState.update { it.copy(isAccepted = isAccepted) }
    }
}
