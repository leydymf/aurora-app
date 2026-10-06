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

    /**
     * Revalida las credenciales de la sesión vigente con la contraseña (RF-20):
     * refresca el login reciente que el proveedor exige para borrar la cuenta.
     */
    suspend fun reauthenticate(password: String)

    /** Elimina la cuenta de la sesión vigente en el proveedor (RF-20 criterio 3). */
    suspend fun deleteCurrentUser()

    /**
     * Cambia la contraseña de la sesión vigente (RF-39, cambio conociendo la
     * actual). El proveedor exige login reciente: siempre precedido de
     * [reauthenticate].
     */
    suspend fun updatePassword(newPassword: String)

    /**
     * Pide al proveedor el correo de restablecimiento (RF-46). No requiere
     * sesión: es la vía de quien NO puede entrar. La plantilla del correo tiene
     * identidad 100 % de clima (P-19, decisión de la directora §4.2).
     */
    suspend fun sendPasswordReset(email: String)
}

/**
 * Fallo del proveedor con su código textual (p. ej. ERROR_EMAIL_ALREADY_IN_USE).
 * Tipo propio: mantiene las clases del SDK confinadas a la implementación y
 * permite probar el repositorio en JVM sin clases de Android.
 */
class AuthServiceException(
    val errorCode: String?,
    cause: Throwable? = null
) : Exception(errorCode, cause) {
    companion object {
        /**
         * Código propio para la caída de red: el SDK la reporta con una excepción
         * aparte, no con [errorCode]. RF-20 lo distingue para poder decir con
         * honestidad que sin conexión nada se ha borrado.
         */
        const val ERROR_NETWORK = "ERROR_NETWORK_REQUEST_FAILED"
    }
}
