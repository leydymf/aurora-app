package com.auroraclima.app.access.ui

import com.auroraclima.app.R
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.usecase.ChangeSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.CreateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.FakeCityNameDirectory
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeHasher
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeRepository
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.seed
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.SearchGateResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeCodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val gate = ValidateSecretCodeUseCase(repository, hasher)
    private val viewModel = ChangeCodeViewModel(
        ChangeSecretCodeUseCase(
            gate,
            CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga"))
        )
    )

    private fun TestScope.collectDoneEvents(): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.doneEvents.toList(events)
        }
        return events
    }

    private suspend fun seedUserCode(code: String) = repository.seed(CodeSlot.USER, code, hasher)

    @Test
    fun `una clave vigente incorrecta muestra su error y no emite terminado`() = runTest {
        seedUserCode("cielo2026")
        val events = collectDoneEvents()

        viewModel.onCurrentCodeChange("brisa2026")
        viewModel.onNewCodeChange("lluvia2027")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.access_change_error_current, viewModel.uiState.value.currentErrorRes)
        assertEquals(0, events.size)
        // La clave vigente sigue abriendo la puerta (nada cambió).
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
    }

    @Test
    fun `una clave nueva invalida muestra el error de reglas`() = runTest {
        seedUserCode("cielo2026")
        val events = collectDoneEvents()

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("bucaramanga")
        advanceUntilIdle()
        // El botón queda deshabilitado por las reglas en vivo; el envío se ignora.
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, events.size)
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
    }

    @Test
    fun `un cambio exitoso limpia las claves del estado y emite terminado`() = runTest {
        seedUserCode("cielo2026")
        val events = collectDoneEvents()

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        // Higiene de estado sensible: ninguna clave queda en memoria del estado.
        assertEquals("", viewModel.uiState.value.currentCode)
        assertEquals("", viewModel.uiState.value.newCode)
        assertNull(viewModel.uiState.value.currentErrorRes)
        // Regresión sobre la puerta (RF-12): la vieja no abre, la nueva sí.
        assertEquals(SearchGateResult.PassThrough, gate.inspect("cielo2026"))
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("lluvia2027"))
    }

    @Test
    fun `un fallo del almacenamiento muestra el error generico`() = runTest {
        seedUserCode("cielo2026")

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        advanceUntilIdle()
        repository.failOnWrite = true
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.generalErrorRes)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
