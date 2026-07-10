package com.auroraclima.app.accounts.domain.model

/**
 * Resultado de crear la cuenta (RF-07). Las excepciones de la capa de datos
 * nunca cruzan hacia la UI (ARQUITECTURA §3): todo fallo llega como uno de
 * estos casos, y la UI los traduce a mensajes genéricos que no revelan el
 * propósito real de la app.
 */
sealed interface CreateAccountResult {
    /** Cuenta creada; continúa a la definición del código (RF-07 criterio 1). */
    data object Success : CreateAccountResult

    /** El correo no tiene formato válido. */
    data object InvalidEmail : CreateAccountResult

    /** La contraseña no cumple el mínimo de 8 caracteres (RF-07 criterio 3). */
    data object WeakPassword : CreateAccountResult

    /** El correo ya está registrado; sin revelar datos de terceros (RF-07 criterio 2). */
    data object EmailAlreadyInUse : CreateAccountResult

    /** Cualquier otro fallo (red, servicio): la UI muestra un error genérico. */
    data object GenericFailure : CreateAccountResult
}
