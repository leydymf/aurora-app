package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateAccountUseCaseTest {
    private val repository = FakeAuthRepository()
    private val useCase = CreateAccountUseCase(repository)

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
}
// El fake compartido vive en AuthFakes.kt.
