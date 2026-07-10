package com.auroraclima.app.accounts.domain.model

/**
 * Resultado del proveedor al iniciar sesión (RF-15). Todos los rechazos de
 * credenciales convergen en [InvalidCredentials]: el error visible jamás dice
 * cuál campo falló (criterio 2).
 */
sealed interface SignInResult {
    data object Success : SignInResult

    /** Correo o contraseña rechazados, sin distinguir cuál. */
    data object InvalidCredentials : SignInResult

    /** Red caída, servicio no disponible, etc.: no cuenta como intento fallido. */
    data object GenericFailure : SignInResult
}
