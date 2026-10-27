package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cierre de la activación (v0.3.3): el recorrido por Inicio queda pendiente solo si
 * el código de acceso se guardó, y un fallo al marcarlo no cambia el resultado.
 */
class CompleteCodeSetupUseCaseTest {
    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val homeTour = FakeHomeTourRepository()
    private val useCase = CompleteCodeSetupUseCase(
        CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga")),
        homeTour
    )

    @Test
    fun `un codigo guardado deja pendiente el recorrido`() = runTest {
        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.Created, result)
        assertNotNull(repository.storedCode(CodeSlot.USER))
        assertTrue(homeTour.pending)
        assertEquals(1, homeTour.markCalls)
    }

    @Test
    fun `un codigo rechazado por las reglas no lo deja pendiente`() = runTest {
        // Nombre de una ciudad real: la regla visible lo rechaza.
        val result = useCase("bucaramanga")

        assertTrue(result is CreateSecretCodeResult.Rejected)
        assertEquals(0, homeTour.markCalls)
    }

    @Test
    fun `un fallo del almacenamiento no lo deja pendiente`() = runTest {
        repository.failOnWrite = true

        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.StorageFailure, result)
        assertEquals(0, homeTour.markCalls)
    }

    @Test
    fun `si la marca no se puede guardar el codigo queda creado igual`() = runTest {
        homeTour.failWrites = true

        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.Created, result)
        assertNotNull(repository.storedCode(CodeSlot.USER))
        assertFalse(homeTour.pending)
    }

    @Test
    fun `las reglas en vivo son las de la creacion`() = runTest {
        val checks = useCase.check("bucaramanga")

        assertTrue(checks.hasMinLength)
        assertFalse(checks.isNotRealCity)
    }
}
