package com.auroraclima.app.accounts.ui

import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.usecase.DeleteAccountUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.PendingErasureFlag
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

/**
 * La UI de RF-20: dos pasos de confirmación, contraseña que nunca se retiene,
 * y errores solo como recursos — el rechazo de contraseña usa el MISMO mensaje
 * del inicio de sesión y la falta de red tiene mensaje propio y honesto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    private val pendingFlag = object : PendingErasureFlag {
        var raised = false

        override suspend fun raise(): Boolean {
            raised = true
            return true
        }

        override suspend fun isRaised(): Boolean = raised

        override suspend fun clear() {
            raised = false
        }
    }

    private fun createViewModel() = DeleteAccountViewModel(
        DeleteAccountUseCase(repository, emptySet(), emptySet(), pendingFlag)
    )

    private fun TestScope.collectDoneEvents(viewModel: DeleteAccountViewModel): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.doneEvents.toList(events)
        }
        return events
    }

    private fun DeleteAccountViewModel.reachPasswordStep(password: String = "contrasena-1") {
        onStartConfirmation()
        onScopeConfirmed()
        onPasswordChange(password)
    }

    @Test
    fun `la confirmacion exige los dos pasos en orden`() = runTest {
        // RF-20 criterio 1: segundo paso explícito.
        val viewModel = createViewModel()
        assertEquals(DeleteAccountStep.Overview, viewModel.uiState.value.step)

        viewModel.onStartConfirmation()
        assertEquals(DeleteAccountStep.ConfirmScope, viewModel.uiState.value.step)

        viewModel.onScopeConfirmed()
        assertEquals(DeleteAccountStep.ConfirmPassword, viewModel.uiState.value.step)
    }

    @Test
    fun `cancelar el dialogo descarta la contrasena tecleada`() = runTest {
        val viewModel = createViewModel()
        viewModel.reachPasswordStep()

        viewModel.onDismissDialogs()

        assertEquals(DeleteAccountStep.Overview, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `la eliminacion completa emite el evento y limpia la contrasena`() = runTest {
        val viewModel = createViewModel()
        val events = collectDoneEvents(viewModel)
        viewModel.reachPasswordStep()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isWorking)
        assertNull(viewModel.uiState.value.errorRes)
    }

    @Test
    fun `la contrasena rechazada muestra el mismo mensaje generico del login`() = runTest {
        // Indistinguibilidad (RF-15 criterio 2) también aquí.
        repository.nextReauthenticateResult = ReauthenticateResult.InvalidCredentials
        val viewModel = createViewModel()
        val events = collectDoneEvents(viewModel)
        viewModel.reachPasswordStep("contrasena-mala")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, events.size)
        assertEquals(R.string.account_login_error, viewModel.uiState.value.errorRes)
        assertEquals(DeleteAccountStep.ConfirmPassword, viewModel.uiState.value.step)
    }

    @Test
    fun `sin red se explica que nada se ha eliminado`() = runTest {
        repository.nextReauthenticateResult = ReauthenticateResult.NoConnection
        val viewModel = createViewModel()
        viewModel.reachPasswordStep()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_delete_error_offline, viewModel.uiState.value.errorRes)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun `el fallo generico permite reintentar desde el mismo dialogo`() = runTest {
        repository.nextDeleteResult = DeleteUserResult.GenericFailure
        val viewModel = createViewModel()
        viewModel.reachPasswordStep()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_delete_error_generic, viewModel.uiState.value.errorRes)
        assertEquals(DeleteAccountStep.ConfirmPassword, viewModel.uiState.value.step)

        // Reintento con red recuperada: el mismo diálogo completa el flujo.
        repository.nextDeleteResult = DeleteUserResult.Success
        val events = collectDoneEvents(viewModel)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
    }

    @Test
    fun `sin contrasena no se envia nada`() = runTest {
        val viewModel = createViewModel()
        viewModel.onStartConfirmation()
        viewModel.onScopeConfirmed()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, repository.reauthenticateCalls)
    }
}
