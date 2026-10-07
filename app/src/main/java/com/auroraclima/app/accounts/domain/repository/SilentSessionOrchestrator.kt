package com.auroraclima.app.accounts.domain.repository

import com.auroraclima.app.accounts.domain.model.SilentSessionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato del reingreso silencioso (P-31 opción C) hacia la UI: la entrada con
 * blob lo arranca y las pantallas observan su estado sin importar de `data`
 * (§2). La implementación vive en `accounts/data` (SilentSessionCoordinator).
 */
interface SilentSessionOrchestrator {
    /** Estado observable de la confirmación en segundo plano. */
    val state: StateFlow<SilentSessionState>

    /** El silencioso fue rechazado: interponer la pantalla de contraseña. */
    val loginRequests: SharedFlow<Unit>

    /** Arranca la confirmación en segundo plano. Idempotente por sesión. */
    fun start()
}
