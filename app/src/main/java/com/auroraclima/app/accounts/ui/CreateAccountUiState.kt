package com.auroraclima.app.accounts.ui

import androidx.annotation.StringRes

/**
 * Estado de la creación de cuenta (RF-07), mockup alta-fidelidad-09 (P05).
 * Los errores viajan como recursos de texto: mensajes genéricos en ciruela
 * sereno que no revelan el propósito (decisión F2 «errores sin alarma»).
 */
data class CreateAccountUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmVisible: Boolean = false,
    val isLoading: Boolean = false,
    @StringRes val emailErrorRes: Int? = null,
    @StringRes val passwordErrorRes: Int? = null,
    @StringRes val confirmErrorRes: Int? = null,
    @StringRes val generalErrorRes: Int? = null
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && confirmPassword.isNotEmpty() && !isLoading
}
