package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Consentimiento informado (RF-17, RF-18). Mientras la usuaria no acepte y cree
 * su cuenta, esta pantalla no recolecta ni persiste dato alguno (RF-18 por
 * construcción): la aceptación solo habilita continuar y no se guarda.
 *
 * Ya no descarta nada: el descarte de una activación sin terminar (hallazgo A de
 * v0.3.2) pasó a la bienvenida, que desde v0.3.2 es la entrada del flujo.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ConsentUiState())
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    fun onAcceptanceChange(isAccepted: Boolean) {
        _uiState.update { it.copy(isAccepted = isAccepted) }
    }
}
