package com.auroraclima.app.access.ui

import androidx.annotation.StringRes

/**
 * Estado del cambio de la clave personal (RF-09), mockup alta-fidelidad-34
 * (P21·b) con dos adaptaciones registradas: la credencial pedida es la clave
 * vigente — no la contraseña (decisión de la usuaria, sesión C1; nota en RF-09)
 * — y la redacción de camuflaje de P-17 («clave», nunca «código secreto»).
 * Las claves en claro viven solo aquí mientras se teclean y se limpian al
 * guardar; jamás tocan disco, Firebase ni logs (RF-08 criterio 3).
 */
data class ChangeCodeUiState(
    val currentCode: String = "",
    val newCode: String = "",
    val isCurrentVisible: Boolean = false,
    val isNewVisible: Boolean = false,
    val isLoading: Boolean = false,
    val hasMinLength: Boolean = false,
    val isNotRealCity: Boolean = false,
    @StringRes val currentErrorRes: Int? = null,
    @StringRes val generalErrorRes: Int? = null
) {
    /** Regla de ciudad para pintar: cubre el desfase entre la tecla y su verificación. */
    val cityRuleSatisfied: Boolean
        get() = isNotRealCity && newCode.isNotBlank()

    val canSubmit: Boolean
        get() = currentCode.isNotBlank() && hasMinLength && isNotRealCity && !isLoading
}
