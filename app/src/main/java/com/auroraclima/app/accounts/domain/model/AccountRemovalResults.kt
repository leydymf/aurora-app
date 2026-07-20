package com.auroraclima.app.accounts.domain.model

/**
 * Resultado de revalidar la contraseña de la sesión vigente (RF-20 paso 1).
 * Como en RF-15, todo rechazo de credenciales converge en un único caso: el
 * mensaje visible no distingue contraseña errónea de cuenta inexistente.
 */
sealed interface ReauthenticateResult {
    data object Success : ReauthenticateResult

    data object InvalidCredentials : ReauthenticateResult

    /** Sin red no se inicia el borrado: la UI explica que nada se ha eliminado. */
    data object NoConnection : ReauthenticateResult

    data object GenericFailure : ReauthenticateResult
}

/** Resultado de eliminar la cuenta del proveedor (RF-20 criterio 3). */
sealed interface DeleteUserResult {
    data object Success : DeleteUserResult

    data object NoConnection : DeleteUserResult

    data object GenericFailure : DeleteUserResult
}

/** Resultado final de la eliminación total (RF-20), lo único que ve la UI. */
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    data object InvalidCredentials : DeleteAccountResult

    data object NoConnection : DeleteAccountResult

    data object GenericFailure : DeleteAccountResult
}
