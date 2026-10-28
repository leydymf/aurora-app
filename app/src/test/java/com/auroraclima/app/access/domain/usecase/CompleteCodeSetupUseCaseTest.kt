package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cierre de la activación (v0.3.3; marca de la guía desde v0.3.4): la guía de Aurora queda pendiente desde el inicio solo si
 * el código de acceso se guardó, y un fallo al marcarlo no cambia el resultado.
 */
class CompleteCodeSetupUseCaseTest {
    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val walkthrough = FakeWalkthroughRepository()
    private val useCase = CompleteCodeSetupUseCase(
        CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga")),
        walkthrough
    )

    @Test
    fun `un codigo guardado deja pendiente la guia`() = runTest {
        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.Created, result)
        assertNotNull(repository.storedCode(CodeSlot.USER))
        assertEquals(WalkthroughMark.FROM_START, walkthrough.stored)
        assertEquals(1, walkthrough.markCalls)
    }

    @Test
    fun `un codigo rechazado por las reglas no lo deja pendiente`() = runTest {
        // Nombre de una ciudad real: la regla visible lo rechaza.
        val result = useCase("bucaramanga")

        assertTrue(result is CreateSecretCodeResult.Rejected)
        assertEquals(0, walkthrough.markCalls)
    }

    @Test
    fun `un fallo del almacenamiento no lo deja pendiente`() = runTest {
        repository.failOnWrite = true

        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.StorageFailure, result)
        assertEquals(0, walkthrough.markCalls)
    }

    @Test
    fun `si la marca no se puede guardar el codigo queda creado igual`() = runTest {
        walkthrough.failWrites = true

        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.Created, result)
        assertNotNull(repository.storedCode(CodeSlot.USER))
        assertEquals(null, walkthrough.stored)
    }

    @Test
    fun `las reglas en vivo son las de la creacion`() = runTest {
        val checks = useCase.check("bucaramanga")

        assertTrue(checks.hasMinLength)
        assertFalse(checks.isNotRealCity)
    }
}
