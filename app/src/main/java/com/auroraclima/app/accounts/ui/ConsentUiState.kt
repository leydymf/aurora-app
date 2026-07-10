package com.auroraclima.app.accounts.ui

/**
 * Estado del consentimiento informado (RF-17). Nace SIEMPRE sin aceptar:
 * ninguna casilla preseleccionada (criterio 1).
 */
data class ConsentUiState(
    val isAccepted: Boolean = false
)
