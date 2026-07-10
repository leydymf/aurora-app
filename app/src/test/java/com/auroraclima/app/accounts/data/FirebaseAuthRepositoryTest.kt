package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
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
}

private class FakeAuthService : AuthService {
    var nextError: Exception? = null

    override suspend fun createUser(email: String, password: String) {
        nextError?.let { throw it }
    }

    override fun signOut() = Unit
}
