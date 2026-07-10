package com.auroraclima.app.access.ui

import androidx.annotation.StringRes

/**
 * Estado de la creación de la clave personal (RF-08), mockup alta-fidelidad-10
 * (P06) con la redacción de camuflaje aprobada en D3 («clave personal», P-17) y
 * el campo de confirmación pedido por la usuaria. Las dos primeras reglas del
 * mockup se verifican en vivo; la tercera («nunca se guarda en texto legible»)
 * es una propiedad del sistema y se pinta informativa.
 */
data class CodeSetupUiState(
    val code: String = "",
    val confirmCode: String = "",
    val isCodeVisible: Boolean = false,
    val isConfirmVisible: Boolean = false,
    val isLoading: Boolean = false,
    val hasMinLength: Boolean = false,
    val isNotRealCity: Boolean = false,
    @StringRes val confirmErrorRes: Int? = null,
    @StringRes val generalErrorRes: Int? = null
) {
    /** Regla de ciudad para pintar: cubre el desfase entre la tecla y su verificación. */
    val cityRuleSatisfied: Boolean
        get() = isNotRealCity && code.isNotBlank()

    val canSubmit: Boolean
        get() = hasMinLength && isNotRealCity && confirmCode.isNotEmpty() && !isLoading
}
