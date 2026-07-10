package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Consentimiento informado (RF-17, RF-18). Deliberadamente sin repositorios:
 * mientras la usuaria no acepte y cree su cuenta, esta pantalla no puede
 * recolectar ni persistir dato alguno (RF-18 por construcción). La aceptación
 * solo habilita continuar; no se guarda en ningún almacenamiento.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ConsentUiState())
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    fun onAcceptanceChange(isAccepted: Boolean) {
        _uiState.update { it.copy(isAccepted = isAccepted) }
    }
}
