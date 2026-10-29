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
import com.auroraclima.app.core.security.MonotonicClock
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

    // Sus temporizadores van en un planificador aparte que nunca avanza: aquí la sesión
    // solo se cierra cuando la prueba toca la nube (advanceUntilIdle no la caduca).
    private val sessionManager = SessionManager(
        scope = CoroutineScope(StandardTestDispatcher()),
        monotonicClock = MonotonicClock { 0L },
        boundCleaners = emptySet()
    ).apply { startSession() }
    private val viewModel = ChangeCodeViewModel(
        ChangeSecretCodeUseCase(
            gate,
            CreateSecretCodeUseCase(repository, hasher, FakeCityNameDirectory("bucaramanga"))
        ),
        sessionManager
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
        viewModel.onConfirmCodeChange("lluvia2027")
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
        viewModel.onConfirmCodeChange("bucaramanga")
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
        viewModel.onConfirmCodeChange("lluvia2027")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        // Higiene de estado sensible: ninguna clave queda en memoria del estado.
        assertEquals("", viewModel.uiState.value.currentCode)
        assertEquals("", viewModel.uiState.value.newCode)
        assertEquals("", viewModel.uiState.value.confirmCode)
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
        viewModel.onConfirmCodeChange("lluvia2027")
        advanceUntilIdle()
        repository.failOnWrite = true
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.generalErrorRes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `una confirmacion distinta no guarda y el error no repite los codigos`() = runTest {
        // v0.3.2 (evaluación heurística): un error al teclear el código nuevo
        // dejaba a la persona sin acceso. Ahora no se guarda nada.
        seedUserCode("cielo2026")
        val events = collectDoneEvents()

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        viewModel.onConfirmCodeChange("lluvia2072")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.access_setup_error_mismatch, viewModel.uiState.value.confirmErrorRes)
        assertEquals(0, events.size)
        assertFalse(viewModel.uiState.value.isLoading)
        // El código vigente sigue abriendo y el nuevo no (nada cambió).
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
        assertEquals(SearchGateResult.PassThrough, gate.inspect("lluvia2027"))
    }

    @Test
    fun `sin confirmacion no se puede enviar`() = runTest {
        seedUserCode("cielo2026")

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `la confirmacion compara las formas recortadas como la creacion`() = runTest {
        // Un espacio accidental no fabrica un desacuerdo: se hashea recortado.
        seedUserCode("cielo2026")
        val events = collectDoneEvents()

        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        viewModel.onConfirmCodeChange("lluvia2027 ")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("lluvia2027"))
    }

    @Test
    fun `corregir la confirmacion limpia su error`() = runTest {
        seedUserCode("cielo2026")
        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        viewModel.onConfirmCodeChange("otra-cosa")
        advanceUntilIdle()
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onConfirmCodeChange("lluvia2027")

        assertNull(viewModel.uiState.value.confirmErrorRes)
    }

    @Test
    fun `salir con la nube durante el calculo lo cancela y no cambia ni avisa nada`() = runTest {
        // RF-16: el Keystore lento (ANR de API 30) ya no retiene la nube; el cálculo
        // en curso se cancela con el cierre y su resultado tardío no llega.
        seedUserCode("cielo2026")
        val events = collectDoneEvents()
        viewModel.onCurrentCodeChange("cielo2026")
        viewModel.onNewCodeChange("lluvia2027")
        viewModel.onConfirmCodeChange("lluvia2027")
        advanceUntilIdle()
        val slowHash = CompletableDeferred<Unit>()
        hasher.hold = slowHash
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals("el cálculo está en curso", 1, hasher.inFlight)

        // El cálculo termina justo después del cierre, antes de que corra nada más:
        // solo una cancelación hecha en el acto, dentro de la limpieza, lo descarta.
        sessionManager.closeNow()
        slowHash.complete(Unit)
        hasher.hold = null
        advanceUntilIdle()

        assertEquals(0, events.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.currentCode)
        assertEquals("", viewModel.uiState.value.newCode)
        assertEquals(SearchGateResult.OpenAccess, gate.inspect("cielo2026"))
        assertEquals(SearchGateResult.PassThrough, gate.inspect("lluvia2027"))
    }
}
