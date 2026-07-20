package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
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
}
