package com.auroraclima.app.accounts.ui

import androidx.annotation.StringRes

/**
 * Estado del cambio de contraseña (RF-39). Contraseñas solo en memoria del
 * estado mientras se teclean; se limpian al culminar (higiene, como el login).
 */
data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isCurrentVisible: Boolean = false,
    val isNewVisible: Boolean = false,
    val isConfirmVisible: Boolean = false,
    val isLoading: Boolean = false,
    @StringRes val currentErrorRes: Int? = null,
    @StringRes val newErrorRes: Int? = null,
    @StringRes val confirmErrorRes: Int? = null,
    @StringRes val generalErrorRes: Int? = null
) {
    val canSubmit: Boolean =
        currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && !isLoading
}
