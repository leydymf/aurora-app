package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.accounts.domain.model.SilentSessionState
import com.auroraclima.app.accounts.domain.repository.SilentSessionOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Puente de observación del reingreso silencioso (P-31 opción C) para la
 * navegación raíz: expone el chip «Sin conexión» y las peticiones de login
 * manual sin que la UI importe de `data` (§2). Va scoped a la Activity — debe
 * sobrevivir a los destinos del grafo seguro para poder interponer el login.
 */
@HiltViewModel
class SilentSessionViewModel @Inject constructor(
    orchestrator: SilentSessionOrchestrator
) : ViewModel() {
    /** True mientras se opera en local (chip sobrio, sin jerga técnica). */
    val isOffline: StateFlow<Boolean> = orchestrator.state
        .map { it == SilentSessionState.Offline }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** El silencioso fue rechazado: interponer la pantalla de contraseña. */
    val loginRequests: SharedFlow<Unit> = orchestrator.loginRequests

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
