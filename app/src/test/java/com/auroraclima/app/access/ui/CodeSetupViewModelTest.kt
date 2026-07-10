package com.auroraclima.app.access.ui

import com.auroraclima.app.R
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.usecase.CreateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.FakeCityNameDirectory
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeHasher
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeRepository
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val viewModel = CodeSetupViewModel(
        CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga", "bogota"))
    )

    private fun TestScope.collectContinueEvents(): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.continueEvents.toList(events)
        }
        return events
    }

    @Test
    fun `las reglas se verifican en vivo mientras se teclea`() = runTest {
        // RF-08 criterio 2: la lista del mockup P06 reacciona a cada tecla
        viewModel.onCodeChange("ciel")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasMinLength)

        viewModel.onCodeChange("cielo2026")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMinLength)
        assertTrue(viewModel.uiState.value.isNotRealCity)

        viewModel.onCodeChange("bucaramanga")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMinLength)
        assertFalse(viewModel.uiState.value.isNotRealCity)
    }

    @Test
    fun `una clave valida confirmada se guarda como hash y emite continuar`() = runTest {
        // RF-08 criterios 1 y 3
        val events = collectContinueEvents()

        viewModel.onCodeChange("cielo2026")
        viewModel.onConfirmCodeChange("cielo2026")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        val stored = repository.storedCode(CodeSlot.USER)
        assertNotNull(stored)
        assertTrue(hasher.hash(stored!!.salt, "cielo2026").contentEquals(stored.hash))
        // Higiene: la clave no permanece en el estado tras guardarse
        assertEquals("", viewModel.uiState.value.code)
        assertEquals("", viewModel.uiState.value.confirmCode)
    }

    @Test
    fun `una confirmacion distinta se rechaza sin guardar`() = runTest {
        val events = collectContinueEvents()

        viewModel.onCodeChange("cielo2026")
        viewModel.onConfirmCodeChange("cielo2027")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.access_setup_error_mismatch, viewModel.uiState.value.confirmErrorRes)
        assertNull(repository.storedCode(CodeSlot.USER))
        assertEquals(0, events.size)
    }

    @Test
    fun `corregir la confirmacion limpia su error`() = runTest {
        viewModel.onCodeChange("cielo2026")
        viewModel.onConfirmCodeChange("cielo2027")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onConfirmCodeChange("cielo2026")

        assertNull(viewModel.uiState.value.confirmErrorRes)
    }

    @Test
    fun `sin reglas cumplidas no se puede enviar`() = runTest {
        // Clave corta: el botón queda deshabilitado y el envío se ignora
        viewModel.onCodeChange("ciel")
        viewModel.onConfirmCodeChange("ciel")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertNull(repository.storedCode(CodeSlot.USER))
    }

    @Test
    fun `un fallo del almacenamiento muestra el error generico`() = runTest {
        repository.failOnWrite = true

        viewModel.onCodeChange("cielo2026")
        viewModel.onConfirmCodeChange("cielo2026")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.generalErrorRes)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
// Los fakes compartidos viven en access/domain/usecase/SecretCodeFakes.kt.
