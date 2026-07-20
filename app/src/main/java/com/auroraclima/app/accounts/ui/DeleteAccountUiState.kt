package com.auroraclima.app.accounts.ui

import androidx.annotation.StringRes

/** Paso visible del flujo de eliminación (RF-20 criterio 1: dos confirmaciones). */
enum class DeleteAccountStep {
    /** Pantalla informativa: alcance e irreversibilidad, sin diálogo abierto. */
    Overview,

    /** Primer diálogo: confirmar que entiende qué se elimina. */
    ConfirmScope,

    /** Segundo diálogo: contraseña como confirmación fuerte (re-auth). */
    ConfirmPassword
}

/**
 * Estado de la eliminación de cuenta (RF-20). Los errores son solo recursos
 * (@StringRes): ningún mensaje crudo del proveedor puede llegar a la UI (§3).
 */
data class DeleteAccountUiState(
    val step: DeleteAccountStep = DeleteAccountStep.Overview,
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isWorking: Boolean = false,
    @StringRes val errorRes: Int? = null
) {
    val canSubmit: Boolean get() = password.isNotEmpty() && !isWorking
}
