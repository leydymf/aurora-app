package com.auroraclima.app.accounts.data

/**
 * Fuente de autenticación cruda. Los fallos con código conocido del proveedor
 * llegan como [AuthServiceException]; el repositorio es el único que los captura
 * y traduce a resultados de dominio.
 */
interface AuthService {
    suspend fun createUser(email: String, password: String)

    suspend fun signIn(email: String, password: String)

    /** True si el proveedor conserva una sesión iniciada. */
    fun hasActiveSession(): Boolean

    /** Cierra la sesión persistida del proveedor. Idempotente. */
    fun signOut()
}

/**
 * Fallo del proveedor con su código textual (p. ej. ERROR_EMAIL_ALREADY_IN_USE).
 * Tipo propio: mantiene las clases del SDK confinadas a la implementación y
 * permite probar el repositorio en JVM sin clases de Android.
 */
class AuthServiceException(
    val errorCode: String?,
    cause: Throwable? = null
) : Exception(errorCode, cause)
