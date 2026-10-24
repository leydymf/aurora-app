package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckAccessCodeUseCaseTest {
    private val repository = FakeSecretCodeRepository()
    private val hasher = FakeSecretCodeHasher()
    private val useCase = CheckAccessCodeUseCase(repository)

    @Test
    fun `solo con el codigo de activacion el codigo de acceso no existe`() = runTest {
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)

        assertEquals(false, useCase.isSet().getOrNull())
    }

    @Test
    fun `con la ranura de la usuaria el codigo de acceso existe`() = runTest {
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)
        repository.seed(CodeSlot.USER, "mi-codigo-1", hasher)

        assertEquals(true, useCase.isSet().getOrNull())
    }

    @Test
    fun `un fallo del almacenamiento se entrega como fallo y no como respuesta`() = runTest {
        // Cada consumidor decide hacia su lado seguro; aquí no se adivina.
        repository.failOnRead = true

        assertTrue(useCase.isSet().isFailure)
    }
}
