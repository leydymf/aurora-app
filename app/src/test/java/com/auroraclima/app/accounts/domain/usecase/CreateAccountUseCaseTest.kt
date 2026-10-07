package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateAccountUseCaseTest {
    private val repository = FakeAuthRepository()
    private val syncKeys = FakeSyncKeyManager()
    private val credentials = FakeCredentialStore()
    private val useCase = CreateAccountUseCase(repository, syncKeys, credentials)

    @Test
    fun `con correo valido y contrasena de 8 o mas la cuenta se crea`() = runTest {
        // RF-07 criterio 1
        val result = useCase("usuaria@correo.co", "clave-segura-1")

        assertEquals(CreateAccountResult.Success, result)
        assertEquals(listOf("usuaria@correo.co"), repository.requestedEmails)
    }

    @Test
    fun `el correo se normaliza recortando espacios`() = runTest {
        useCase("  usuaria@correo.co ", "clave-segura-1")

        assertEquals(listOf("usuaria@correo.co"), repository.requestedEmails)
    }

    @Test
    fun `un correo mal formado se rechaza sin tocar la red`() = runTest {
        assertEquals(CreateAccountResult.InvalidEmail, useCase("sin-arroba", "clave-segura-1"))
        assertEquals(CreateAccountResult.InvalidEmail, useCase("dos@arro@bas.co", "clave-segura-1"))
        assertEquals(CreateAccountResult.InvalidEmail, useCase("sin@punto", "clave-segura-1"))
        assertEquals(0, repository.requestedEmails.size)
    }

    @Test
    fun `una contrasena de menos de 8 se rechaza sin tocar la red`() = runTest {
        // RF-07 criterio 3
        val result = useCase("usuaria@correo.co", "corta7c")

        assertEquals(CreateAccountResult.WeakPassword, result)
        assertEquals(0, repository.requestedEmails.size)
    }

    @Test
    fun `el resultado del repositorio se propaga tal cual`() = runTest {
        // RF-07 criterio 2: correo duplicado rechazado sin revelar datos de terceros
        repository.nextCreateResult = CreateAccountResult.EmailAlreadyInUse

        assertEquals(
            CreateAccountResult.EmailAlreadyInUse,
            useCase("usuaria@correo.co", "clave-segura-1")
        )
    }

    @Test
    fun `la cuenta creada publica su sobre de llaves con la contrasena`() = runTest {
        // RF-39: único momento con la contraseña en memoria además del login.
        useCase("usuaria@correo.co", "clave-segura-1")

        assertEquals(listOf("clave-segura-1"), syncKeys.establishedPasswords)
    }

    @Test
    fun `una creacion rechazada no toca las llaves`() = runTest {
        repository.nextCreateResult = CreateAccountResult.EmailAlreadyInUse

        useCase("usuaria@correo.co", "clave-segura-1")

        assertEquals(0, syncKeys.establishedPasswords.size)
    }

    @Test
    fun `un fallo del sobre no rompe la creacion`() = runTest {
        // Mejor esfuerzo (§3): el primer login reintenta el establecimiento.
        syncKeys.establishOutcome = Result.failure(IllegalStateException())

        assertEquals(CreateAccountResult.Success, useCase("usuaria@correo.co", "clave-segura-1"))
    }

    @Test
    fun `la cuenta creada hace nacer el blob de credenciales normalizado`() = runTest {
        // P-31 opción C: mismo criterio que el sobre — momentos con contraseña.
        useCase("  usuaria@correo.co ", "clave-segura-1")

        assertEquals("usuaria@correo.co", credentials.stored?.email)
        assertEquals("clave-segura-1", credentials.stored?.password)
    }

    @Test
    fun `una creacion rechazada no crea el blob`() = runTest {
        repository.nextCreateResult = CreateAccountResult.EmailAlreadyInUse

        useCase("usuaria@correo.co", "clave-segura-1")

        assertNull(credentials.stored)
    }

    @Test
    fun `un fallo al guardar el blob no rompe la creacion ni el sobre`() = runTest {
        credentials.failNextSave = true

        assertEquals(CreateAccountResult.Success, useCase("usuaria@correo.co", "clave-segura-1"))
        assertEquals(listOf("clave-segura-1"), syncKeys.establishedPasswords)
    }
}
// El fake compartido vive en AuthFakes.kt.
