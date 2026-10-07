package com.auroraclima.app.accounts.ui

import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.PasswordResetResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import com.auroraclima.app.accounts.domain.security.WallClock
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.FakeSignInAttemptStore
import com.auroraclima.app.accounts.domain.usecase.FakeSilentSessionOrchestrator
import com.auroraclima.app.accounts.domain.usecase.NoCloudReconciliation
import com.auroraclima.app.accounts.domain.usecase.SendPasswordResetUseCase
import com.auroraclima.app.accounts.domain.usecase.SignInUseCase
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val store = FakeSignInAttemptStore()
    private val credentials = FakeCredentialStore()
    private val silentSession = FakeSilentSessionOrchestrator()

    /** Reloj de pared anclado al tiempo virtual del test: la cuenta regresiva
     *  se re-ancla al deadline persistido en cada tick y ambos deben avanzar juntos. */
    private val resetAttempts = FakeSignInAttemptStore()

    private fun TestScope.createViewModel(): LoginViewModel {
        val clock = WallClock { BASE_EPOCH_MS + testScheduler.currentTime }
        return LoginViewModel(
            SignInUseCase(
                repository,
                store,
                clock,
                FakeSyncKeyManager(),
                NoCloudReconciliation,
                emptySet(),
                credentials
            ),
            repository,
            credentials,
            silentSession,
            SendPasswordResetUseCase(repository, resetAttempts, clock)
        )
    }

    private fun TestScope.collectOpenEvents(viewModel: LoginViewModel): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.openEvents.toList(events)
        }
        return events
    }

    private fun LoginViewModel.fillAndSubmit(password: String = "clave-segura-1") {
        onEmailChange("usuaria@correo.co")
        onPasswordChange(password)
        onSubmit()
    }

    @Test
    fun `credenciales correctas emiten el acceso y limpian la contrasena`() = runTest {
        // RF-15 criterio 1
        val viewModel = createViewModel()
        runCurrent()
        val events = collectOpenEvents(viewModel)

        viewModel.fillAndSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `con sesion vigente se salta el formulario`() = runTest {
        // RF-12 criterio 1: pantalla principal directa si hay sesión activa
        repository.activeSession = true
        val viewModel = createViewModel()
        val events = collectOpenEvents(viewModel)
        runCurrent()

        assertEquals(1, events.size)
        // El formulario nunca llega a pintarse
        assertTrue(viewModel.uiState.value.isCheckingSession)
        assertEquals(0, repository.signInCalls)
        // Con sesión viva no hay nada que confirmar en segundo plano
        assertEquals(0, silentSession.startCalls)
    }

    @Test
    fun `con blob y sin sesion abre siempre y arranca el silencioso`() = runTest {
        // P-31 opción C: el código correcto abre con los datos locales; la
        // sesión del proveedor se confirma en paralelo, sin formulario.
        credentials.save("usuaria@correo.co", "clave-segura-1")
        val viewModel = createViewModel()
        val events = collectOpenEvents(viewModel)
        runCurrent()

        assertEquals(1, events.size)
        assertTrue(viewModel.uiState.value.isCheckingSession)
        assertEquals(1, silentSession.startCalls)
        // El silencioso corre en el coordinador, no aquí: cero login directo.
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `sin sesion y sin blob se muestra el formulario`() = runTest {
        // Comportamiento previo a la opción C (también tras RF-19/RF-20/rechazo).
        val viewModel = createViewModel()
        val events = collectOpenEvents(viewModel)
        runCurrent()

        assertEquals(0, events.size)
        assertFalse(viewModel.uiState.value.isCheckingSession)
        assertEquals(0, silentSession.startCalls)
    }

    @Test
    fun `el rechazo muestra un unico error generico sin senalar campo`() = runTest {
        // RF-15 criterio 2: el estado solo tiene un campo de error, compartido
        repository.nextSignInResult = SignInResult.InvalidCredentials
        val viewModel = createViewModel()
        runCurrent()
        val events = collectOpenEvents(viewModel)

        viewModel.fillAndSubmit("clave-mala")
        advanceUntilIdle()

        assertEquals(R.string.account_login_error, viewModel.uiState.value.errorRes)
        assertEquals(0, events.size)
        assertFalse(viewModel.uiState.value.isLocked)
    }

    @Test
    fun `al quinto intento fallido aparece la pausa temporal`() = runTest {
        // RF-15 criterio 3, mockup P08·b
        repository.nextSignInResult = SignInResult.InvalidCredentials
        val viewModel = createViewModel()
        runCurrent()

        repeat(5) {
            viewModel.fillAndSubmit("clave-mala")
            runCurrent()
        }

        val state = viewModel.uiState.value
        assertTrue(state.isLocked)
        assertEquals("5:00", state.lockCountdownText)
        assertFalse(state.canSubmit)
        // Higiene: la contraseña no espera en memoria durante la pausa
        assertEquals("", state.password)
        assertNull(state.errorRes)
    }

    @Test
    fun `la cuenta regresiva avanza segundo a segundo y al terminar desbloquea`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        val viewModel = createViewModel()
        runCurrent()
        repeat(5) {
            viewModel.fillAndSubmit("clave-mala")
            runCurrent()
        }

        advanceTimeBy(23_000)
        runCurrent()
        assertEquals("4:37", viewModel.uiState.value.lockCountdownText)

        advanceTimeBy(SignInUseCase.LOCK_DURATION_MS)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLocked)
    }

    @Test
    fun `un bloqueo vigente de una visita anterior se retoma al entrar`() = runTest {
        // El contador persiste: cerrar la app no salta la pausa
        store.stored = SignInAttempts(0, BASE_EPOCH_MS + 90_000)
        val viewModel = createViewModel()
        advanceTimeBy(1)
        runCurrent()

        assertTrue(viewModel.uiState.value.isLocked)
        assertEquals("1:30", viewModel.uiState.value.lockCountdownText)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `un fallo de red muestra el error generico sin bloquear`() = runTest {
        repository.nextSignInResult = SignInResult.GenericFailure
        val viewModel = createViewModel()
        runCurrent()

        viewModel.fillAndSubmit()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
        assertFalse(viewModel.uiState.value.isLocked)
    }

    @Test
    fun `escribir de nuevo limpia el error`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        val viewModel = createViewModel()
        runCurrent()
        viewModel.fillAndSubmit("clave-mala")
        advanceUntilIdle()

        viewModel.onPasswordChange("otra-clave")

        assertNull(viewModel.uiState.value.errorRes)
    }

    private companion object {
        const val BASE_EPOCH_MS = 1_700_000_000_000L
    }

    @Test
    fun `sin campos completos no se puede enviar`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `la pausa del restablecimiento se muestra con el MISMO mensaje que un envio normal`() = runTest {
        // RF-46 criterio 3: decir «estás en pausa» revelaría que alguien ya
        // pidió el restablecimiento para ese correo.
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onResetRequested()
        viewModel.onResetEmailChange("usuaria@correo.co")
        repeat(5) {
            viewModel.onResetSubmit()
            advanceUntilIdle()
            viewModel.onResetEmailChange("usuaria@correo.co")
        }

        viewModel.onResetSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_reset_done, viewModel.uiState.value.resetDoneRes)
        assertNull(viewModel.uiState.value.resetErrorRes)
    }

    @Test
    fun `sin conexion el restablecimiento dice que el correo no salio`() = runTest {
        repository.nextPasswordResetResult = PasswordResetResult.NoConnection
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onResetRequested()
        viewModel.onResetEmailChange("usuaria@correo.co")

        viewModel.onResetSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_reset_offline, viewModel.uiState.value.resetErrorRes)
        assertNull(viewModel.uiState.value.resetDoneRes)
    }
}
