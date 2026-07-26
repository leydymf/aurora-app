package com.auroraclima.app.accounts.domain.model

/** Resultado de cambiar la contraseña en el proveedor (RF-39, cambio conociendo la actual). */
sealed interface UpdatePasswordResult {
    data object Success : UpdatePasswordResult

    data object WeakPassword : UpdatePasswordResult

    data object NoConnection : UpdatePasswordResult

    data object GenericFailure : UpdatePasswordResult
}

/** Resultado final del cambio de contraseña, lo único que ve la UI. */
sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult

    /** La contraseña actual no fue aceptada (mensaje genérico, como RF-15/RF-20). */
    data object InvalidCredentials : ChangePasswordResult

    data object WeakPassword : ChangePasswordResult

    /** Sin red no se inicia el cambio: la UI afirma que nada se modificó. */
    data object NoConnection : ChangePasswordResult

    data object GenericFailure : ChangePasswordResult
}
