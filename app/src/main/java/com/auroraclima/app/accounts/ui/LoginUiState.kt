package com.auroraclima.app.accounts.ui

import androidx.annotation.StringRes
import java.util.Locale

/**
 * Estado del inicio de sesión (RF-15), mockups alta-fidelidad-14/15 (P08 y
 * P08·b). Un único [errorRes] genérico para cualquier rechazo (criterio 2) y
 * bloqueo temporal con cuenta regresiva (criterio 3, «pausa temporal» F3/F4).
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    /** True mientras se decide si ya hay sesión del proveedor (RF-12 criterio 1). */
    val isCheckingSession: Boolean = true,
    /** Segundos restantes del bloqueo, o null si no hay bloqueo. */
    val lockRemainingSeconds: Long? = null,
    @StringRes val errorRes: Int? = null,
    /** Hoja de recuperación por correo (RF-46) visible. */
    val isResetSheetVisible: Boolean = false,
    val resetEmail: String = "",
    val isSendingReset: Boolean = false,
    /** Mensaje final de la hoja: el MISMO exista o no la cuenta. */
    @StringRes val resetDoneRes: Int? = null,
    @StringRes val resetErrorRes: Int? = null
) {
    val isLocked: Boolean
        get() = lockRemainingSeconds != null

    /** Cuenta regresiva del mockup P08·b («4:37»). Locale fijo: solo dígitos occidentales. */
    val lockCountdownText: String?
        get() = lockRemainingSeconds?.let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) }

    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() &&
            !isLoading && !isLocked && !isCheckingSession

    /** Enviar exige un correo escrito y que no haya un envío en curso (RF-46). */
    val canSendReset: Boolean
        get() = resetEmail.isNotBlank() && !isSendingReset && resetDoneRes == null
}
