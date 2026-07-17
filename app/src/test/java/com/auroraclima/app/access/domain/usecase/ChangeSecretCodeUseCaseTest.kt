package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.core.security.SearchGateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cambio de la clave personal (RF-09): la clave vigente autoriza, las reglas de
 * RF-08 rigen sobre la nueva, y el cambio se refleja en la puerta de búsqueda
 * (RF-12) — la vieja deja de abrir y la nueva abre.
 */
class ChangeSecretCodeUseCaseTest {
    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val gate = ValidateSecretCodeUseCase(repository, hasher)
    private val useCase = ChangeSecretCodeUseCase(
        gate,
        CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga", "bogota"))
    )

    @Test
    fun `una clave vigente incorrecta no autoriza el cambio`() = runTest {
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        val result = useCase("brisa2026", "lluvia2027")

        assertEquals(ChangeSecretCodeResult.CurrentCodeMismatch, result)
        // La clave guardada queda intacta: la vigente sigue abriendo la puerta.
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
        assertEquals(SearchGateResult.PassThrough, gate.inspect("lluvia2027"))
    }

    @Test
    fun `el codigo de activacion no autoriza el cambio de la clave de usuaria`() = runTest {
        // Con clave de usuaria creada, la activación quedó reemplazada (RF-08);
        // tampoco puede autorizar un cambio.
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        val result = useCase("luna2026", "lluvia2027")

        assertEquals(ChangeSecretCodeResult.CurrentCodeMismatch, result)
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
    }

    @Test
    fun `una clave nueva invalida se rechaza con las reglas de creacion`() = runTest {
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        val shortResult = useCase("cielo2026", "brisa")
        val cityResult = useCase("cielo2026", "bucaramanga")

        assertTrue(shortResult is ChangeSecretCodeResult.Rejected)
        assertTrue(!(shortResult as ChangeSecretCodeResult.Rejected).checks.hasMinLength)
        assertTrue(cityResult is ChangeSecretCodeResult.Rejected)
        assertTrue(!(cityResult as ChangeSecretCodeResult.Rejected).checks.isNotRealCity)
        // Ningún rechazo tocó la ranura: la clave vigente sigue abriendo.
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
    }

    @Test
    fun `un cambio exitoso reemplaza la clave en la puerta de busqueda`() = runTest {
        // Regresión sobre la puerta (RF-12): la vieja deja de abrir y la nueva abre.
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        val result = useCase("cielo2026", "lluvia2027")

        assertEquals(ChangeSecretCodeResult.Changed, result)
        assertEquals(SearchGateResult.PassThrough, gate.inspect("cielo2026"))
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("lluvia2027"))
    }

    @Test
    fun `un fallo de almacenamiento al guardar se reporta como fallo generico`() = runTest {
        repository.seed(CodeSlot.USER, "cielo2026", hasher)
        repository.failOnWrite = true

        val result = useCase("cielo2026", "lluvia2027")

        assertEquals(ChangeSecretCodeResult.StorageFailure, result)
    }
}
