package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.core.security.SearchGateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSecretCodeUseCaseTest {
    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val cityDirectory = FakeCityNameDirectory("bucaramanga", "bogota", "paris")
    private val useCase = CreateSecretCodeUseCase(repository, hasher, cityDirectory)

    @Test
    fun `un codigo valido queda registrado como hash`() = runTest {
        // RF-08 criterio 1
        val result = useCase("cielo2026")

        assertEquals(CreateSecretCodeResult.Created, result)
        val stored = repository.storedCode(CodeSlot.USER)
        assertNotNull(stored)
        assertTrue(hasher.hash(stored!!.salt, "cielo2026").contentEquals(stored.hash))
    }

    @Test
    fun `el codigo nunca se almacena en texto legible`() = runTest {
        // RF-08 criterio 3
        useCase("cielo2026")

        val stored = repository.storedCode(CodeSlot.USER)!!
        val plaintext = "cielo2026".toByteArray(Charsets.UTF_8)
        assertFalse(stored.hash.contentEquals(plaintext))
        assertFalse(stored.salt.contentEquals(plaintext))
        assertFalse(String(stored.hash, Charsets.ISO_8859_1).contains("cielo2026"))
    }

    @Test
    fun `un codigo corto se rechaza sin guardar nada`() = runTest {
        // RF-08 criterio 2
        val result = useCase("cielo")

        assertTrue(result is CreateSecretCodeResult.Rejected)
        assertFalse((result as CreateSecretCodeResult.Rejected).checks.hasMinLength)
        assertNull(repository.storedCode(CodeSlot.USER))
    }

    @Test
    fun `el nombre de una ciudad real se rechaza sin guardar nada`() = runTest {
        // RF-08 criterio 2
        val result = useCase("bucaramanga")

        assertTrue(result is CreateSecretCodeResult.Rejected)
        assertFalse((result as CreateSecretCodeResult.Rejected).checks.isNotRealCity)
        assertNull(repository.storedCode(CodeSlot.USER))
    }

    @Test
    fun `el codigo se recorta antes de validar y hashear`() = runTest {
        // Coherente con el recorte de la puerta de búsqueda (RF-12): un espacio
        // accidental no puede fabricar una clave imposible de reingresar.
        useCase("  cielo2026 ")

        val stored = repository.storedCode(CodeSlot.USER)!!
        assertTrue(hasher.hash(stored.salt, "cielo2026").contentEquals(stored.hash))
    }

    @Test
    fun `el codigo creado abre el acceso por la puerta de busqueda`() = runTest {
        // Integración RF-08 → RF-12: el flujo real de reingreso usa el mismo hash.
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)
        val gate = ValidateSecretCodeUseCase(repository, hasher)

        useCase("cielo2026")

        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
        // La activación queda reemplazada (nota de RF-08).
        assertEquals(SearchGateResult.PassThrough, gate.inspect("luna2026"))
    }

    @Test
    fun `las reglas en vivo reflejan longitud y ciudad`() = runTest {
        assertFalse(useCase.check("ciel").hasMinLength)
        assertTrue(useCase.check("cielo2026").hasMinLength)
        assertFalse(useCase.check("bucaramanga").isNotRealCity)
        assertTrue(useCase.check("cielo2026").isNotRealCity)
        // Campo vacío: la regla de ciudad no se marca cumplida todavía.
        assertFalse(useCase.check("").isNotRealCity)
    }

    @Test
    fun `un fallo del almacenamiento devuelve error generico sin lanzar`() = runTest {
        repository.failOnWrite = true

        assertEquals(CreateSecretCodeResult.StorageFailure, useCase("cielo2026"))
    }

    @Test
    fun `un fallo del directorio de ciudades no bloquea la creacion`() = runTest {
        // La regla de ciudad es salvaguarda de uso, no barrera criptográfica (§3).
        cityDirectory.failOnRead = true

        assertEquals(CreateSecretCodeResult.Created, useCase("cielo2026"))
    }
}

// Los fakes compartidos (hasher, repositorio, directorio y siembra) viven en SecretCodeFakes.kt.
