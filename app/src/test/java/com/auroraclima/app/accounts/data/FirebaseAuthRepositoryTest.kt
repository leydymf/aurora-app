package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.PasswordResetResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El repositorio traduce cada fallo del proveedor a un resultado de dominio:
 * ninguna excepción cruza hacia la UI (ARQUITECTURA §3). El fake sustituye a
 * Firebase lanzando los mismos fallos tipados que produce el envoltorio del SDK.
 */
class FirebaseAuthRepositoryTest {
    private val service = FakeAuthService()
    private val repository = FirebaseAuthRepository(service)

    @Test
    fun `sin fallo del proveedor la cuenta se crea`() = runTest {
        // RF-07 criterio 1
        assertEquals(
            CreateAccountResult.Success,
            repository.createAccount("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `el correo duplicado se traduce a su resultado`() = runTest {
        // RF-07 criterio 2
        service.nextError = AuthServiceException("ERROR_EMAIL_ALREADY_IN_USE")

        assertEquals(
            CreateAccountResult.EmailAlreadyInUse,
            repository.createAccount("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `la contrasena debil del proveedor se traduce a su resultado`() = runTest {
        service.nextError = AuthServiceException("ERROR_WEAK_PASSWORD")

        assertEquals(
            CreateAccountResult.WeakPassword,
            repository.createAccount("usuaria@correo.co", "12345678")
        )
    }

    @Test
    fun `el correo invalido del proveedor se traduce a su resultado`() = runTest {
        service.nextError = AuthServiceException("ERROR_INVALID_EMAIL")

        assertEquals(
            CreateAccountResult.InvalidEmail,
            repository.createAccount("usuaria@", "clave-segura-1")
        )
    }

    @Test
    fun `un fallo de red es un fallo generico`() = runTest {
        service.nextError = IOException("sin red")

        assertEquals(
            CreateAccountResult.GenericFailure,
            repository.createAccount("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `un codigo de proveedor desconocido es un fallo generico`() = runTest {
        service.nextError = AuthServiceException("ERROR_OPERATION_NOT_ALLOWED")

        assertEquals(
            CreateAccountResult.GenericFailure,
            repository.createAccount("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `sin fallo del proveedor la sesion inicia`() = runTest {
        // RF-15 criterio 1
        assertEquals(
            SignInResult.Success,
            repository.signIn("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `todos los rechazos de credenciales convergen en el mismo resultado`() = runTest {
        // RF-15 criterio 2: el resultado no distingue si falló el correo, la
        // contraseña, el formato o si la cuenta existe
        val credentialCodes = listOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_USER_DISABLED",
            "ERROR_INVALID_EMAIL"
        )

        for (code in credentialCodes) {
            service.nextError = AuthServiceException(code)
            assertEquals(
                "código $code",
                SignInResult.InvalidCredentials,
                repository.signIn("usuaria@correo.co", "clave-mala")
            )
        }
    }

    @Test
    fun `un fallo de red al iniciar sesion es un fallo generico`() = runTest {
        service.nextError = IOException("sin red")

        assertEquals(
            SignInResult.GenericFailure,
            repository.signIn("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `la reautenticacion exitosa se traduce a su resultado`() = runTest {
        // RF-20 paso 1
        assertEquals(
            ReauthenticateResult.Success,
            repository.reauthenticate("contrasena-1")
        )
    }

    @Test
    fun `los rechazos de credenciales al reautenticar convergen en el mismo resultado`() = runTest {
        // Misma indistinguibilidad que RF-15 criterio 2.
        val credentialCodes = listOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_USER_DISABLED",
            "ERROR_INVALID_EMAIL"
        )

        for (code in credentialCodes) {
            service.nextError = AuthServiceException(code)
            assertEquals(
                "código $code",
                ReauthenticateResult.InvalidCredentials,
                repository.reauthenticate("contrasena-mala")
            )
        }
    }

    @Test
    fun `la red caida al reautenticar se distingue del fallo generico`() = runTest {
        // RF-20: la UI promete que sin conexión nada se ha borrado.
        service.nextError = AuthServiceException(AuthServiceException.ERROR_NETWORK)

        assertEquals(
            ReauthenticateResult.NoConnection,
            repository.reauthenticate("contrasena-1")
        )
    }

    @Test
    fun `una excepcion cruda al reautenticar es fallo generico`() = runTest {
        service.nextError = IOException("proveedor caído")

        assertEquals(
            ReauthenticateResult.GenericFailure,
            repository.reauthenticate("contrasena-1")
        )
    }

    @Test
    fun `el borrado de cuenta traduce exito, red caida y fallo generico`() = runTest {
        // RF-20 criterio 3
        assertEquals(DeleteUserResult.Success, repository.deleteCurrentUser())

        service.nextError = AuthServiceException(AuthServiceException.ERROR_NETWORK)
        assertEquals(DeleteUserResult.NoConnection, repository.deleteCurrentUser())

        // Incluye el requires-recent-login residual: reintentable como genérico.
        service.nextError = AuthServiceException("ERROR_REQUIRES_RECENT_LOGIN")
        assertEquals(DeleteUserResult.GenericFailure, repository.deleteCurrentUser())

        service.nextError = IOException("proveedor caído")
        assertEquals(DeleteUserResult.GenericFailure, repository.deleteCurrentUser())
    }

    @Test
    fun `la sesion activa refleja el estado del proveedor`() {
        service.activeSession = false
        assertEquals(false, repository.hasActiveSession())

        service.activeSession = true
        assertEquals(true, repository.hasActiveSession())
    }

    @Test
    fun `el restablecimiento no revela si la cuenta existe`() = runTest {
        // RF-46: cuenta inexistente y correo mal formado deben dar EXACTAMENTE
        // el mismo resultado que un envío correcto. Si se distinguieran, la
        // pantalla serviría para averiguar quién tiene cuenta aquí.
        val conCuenta = repository.sendPasswordReset("usuaria@correo.co")

        service.passwordResetError = AuthServiceException("ERROR_USER_NOT_FOUND")
        val sinCuenta = repository.sendPasswordReset("no.existe@correo.co")

        service.passwordResetError = AuthServiceException("ERROR_INVALID_EMAIL")
        val malFormado = repository.sendPasswordReset("esto no es un correo")

        assertEquals(PasswordResetResult.Requested, conCuenta)
        assertEquals(conCuenta, sinCuenta)
        assertEquals(conCuenta, malFormado)
    }

    @Test
    fun `sin conexion el restablecimiento si se distingue`() = runTest {
        // Mismo criterio que RF-20: sin red el correo NO salió y hay que
        // poder decirlo, o la usuaria esperaría un mensaje que no llega.
        service.passwordResetError = AuthServiceException(AuthServiceException.ERROR_NETWORK)

        assertEquals(PasswordResetResult.NoConnection, repository.sendPasswordReset("usuaria@correo.co"))
    }

    @Test
    fun `el rechazo por demasiadas peticiones tampoco se distingue`() = runTest {
        // Solo se dispara con envíos reales, es decir cuando la cuenta existe:
        // mostrarlo aparte sería un oráculo de existencia (criterio 3).
        service.passwordResetError = AuthServiceException("ERROR_TOO_MANY_REQUESTS")

        assertEquals(PasswordResetResult.Requested, repository.sendPasswordReset("usuaria@correo.co"))
    }
}

private class FakeAuthService : AuthService {
    var nextError: Exception? = null
    var activeSession = false

    override suspend fun createUser(email: String, password: String) {
        nextError?.let { throw it }
    }

    override suspend fun signIn(email: String, password: String) {
        nextError?.let { throw it }
    }

    override fun hasActiveSession(): Boolean = activeSession

    override fun signOut() = Unit

    override suspend fun reauthenticate(password: String) {
        nextError?.let { throw it }
    }

    override suspend fun deleteCurrentUser() {
        nextError?.let { throw it }
    }

    var passwordResetError: Exception? = null

    var passwordResetEmails = mutableListOf<String>()

    override suspend fun sendPasswordReset(email: String) {
        passwordResetEmails += email

        passwordResetError?.let { throw it }
    }

    override suspend fun updatePassword(newPassword: String) {
        nextError?.let { throw it }
    }
}
