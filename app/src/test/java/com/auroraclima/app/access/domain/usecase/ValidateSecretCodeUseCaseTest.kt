package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.core.security.SearchGateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateSecretCodeUseCaseTest {
    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val useCase = ValidateSecretCodeUseCase(repository, hasher)

    @Test
    fun `el codigo de usuaria exacto abre el acceso`() = runTest {
        // RF-12 criterio 1
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        assertEquals(SearchGateResult.OpenAccess, useCase.inspect("cielo2026"))
    }

    @Test
    fun `el codigo con espacios alrededor tambien abre`() = runTest {
        // Coherente con el recorte que aplica la búsqueda de ciudades
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        assertEquals(SearchGateResult.OpenAccess, useCase.inspect("  cielo2026 "))
    }

    @Test
    fun `cualquier variacion del codigo pasa como busqueda normal`() = runTest {
        // RF-12 criterio 2, RF-13 criterio 2
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        assertEquals(SearchGateResult.PassThrough, useCase.inspect("cielo2027"))
        assertEquals(SearchGateResult.PassThrough, useCase.inspect("Cielo2026"))
        assertEquals(SearchGateResult.PassThrough, useCase.inspect("Bucaramanga"))
    }

    @Test
    fun `la activacion sin codigo de usuaria abre el registro`() = runTest {
        // RF-05 criterio 1
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)

        assertEquals(SearchGateResult.OpenActivation, useCase.inspect("luna2026"))
    }

    @Test
    fun `la activacion deja de abrir cuando ya existe codigo de usuaria`() = runTest {
        // RF-08: el código personal reemplaza al de activación
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)
        repository.seed(CodeSlot.USER, "cielo2026", hasher)

        assertEquals(SearchGateResult.PassThrough, useCase.inspect("luna2026"))
        assertEquals(SearchGateResult.OpenAccess, useCase.inspect("cielo2026"))
    }

    @Test
    fun `sin codigos almacenados todo pasa como busqueda normal`() = runTest {
        assertEquals(SearchGateResult.PassThrough, useCase.inspect("cielo2026"))
    }

    @Test
    fun `las consultas cortas no llegan al hasher`() = runTest {
        // Los códigos tienen mínimo 6 caracteres (RF-08); una consulta corta ni se hashea
        repository.seed(CodeSlot.USER, "cielo2026", hasher)
        hasher.hashCallCount = 0

        assertEquals(SearchGateResult.PassThrough, useCase.inspect("bucar"))
        assertEquals(0, hasher.hashCallCount)
    }

    @Test
    fun `un fallo interno de la puerta pasa como busqueda normal`() = runTest {
        // §3 de ARQUITECTURA.md: las excepciones nunca cruzan hacia la UI; degradar en
        // silencio a búsqueda normal es el comportamiento seguro (RF-13)
        repository.seed(CodeSlot.USER, "cielo2026", hasher)
        repository.failOnRead = true

        assertEquals(SearchGateResult.PassThrough, useCase.inspect("cielo2026"))
    }

    @Test
    fun `un fallo del hasher tambien pasa como busqueda normal`() = runTest {
        // El Keystore de Android puede fallar en la práctica (llave invalidada,
        // actualización del sistema); la fachada no debe enterarse
        repository.seed(CodeSlot.USER, "cielo2026", hasher)
        hasher.failOnHash = true

        assertEquals(SearchGateResult.PassThrough, useCase.inspect("cielo2026"))
    }

    @Test
    fun `un candidato que no coincide se hashea completo igual que uno correcto`() = runTest {
        // Sin atajos previos al hash: la validación no es observablemente más rápida
        // ante un fallo (RF-13)
        repository.seed(CodeSlot.USER, "cielo2026", hasher)
        hasher.hashCallCount = 0

        useCase.inspect("xxxxxxxxx")
        assertEquals(1, hasher.hashCallCount)

        hasher.hashCallCount = 0
        useCase.inspect("cielo2026")
        assertEquals(1, hasher.hashCallCount)
    }
}

// Los fakes compartidos (hasher, repositorio y siembra) viven en SecretCodeFakes.kt.
