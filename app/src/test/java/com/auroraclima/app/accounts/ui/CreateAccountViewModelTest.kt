package com.auroraclima.app.accounts.ui

import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.usecase.CreateAccountUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class CreateAccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = CreateAccountViewModel(
        CreateAccountUseCase(repository, FakeSyncKeyManager(), FakeCredentialStore())
    )

    private fun fillValidForm() {
        viewModel.onEmailChange("usuaria@correo.co")
        viewModel.onPasswordChange("clave-segura-1")
        viewModel.onConfirmPasswordChange("clave-segura-1")
    }

    private fun TestScope.collectContinueEvents(): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.continueEvents.toList(events)
        }
        return events
    }

    @Test
    fun `con datos validos se crea la cuenta y se emite continuar`() = runTest {
        // RF-07 criterio 1: continúa a la definición del código secreto
        val events = collectContinueEvents()

        fillValidForm()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertFalse(viewModel.uiState.value.isLoading)
        // Higiene: la contraseña no permanece en el estado tras usarse
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirmPassword)
    }

    @Test
    fun `contrasenas que no coinciden se rechazan sin tocar la red`() = runTest {
        viewModel.onEmailChange("usuaria@correo.co")
        viewModel.onPasswordChange("clave-segura-1")
        viewModel.onConfirmPasswordChange("clave-segura-2")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_error_password_mismatch, viewModel.uiState.value.confirmErrorRes)
        assertEquals(0, repository.createCalls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `corregir la confirmacion limpia su error`() = runTest {
        viewModel.onEmailChange("usuaria@correo.co")
        viewModel.onPasswordChange("clave-segura-1")
        viewModel.onConfirmPasswordChange("clave-segura-2")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onConfirmPasswordChange("clave-segura-1")

        assertNull(viewModel.uiState.value.confirmErrorRes)
    }

    @Test
    fun `un correo duplicado muestra su mensaje sin emitir continuar`() = runTest {
        // RF-07 criterio 2
        val events = collectContinueEvents()
        repository.nextCreateResult = CreateAccountResult.EmailAlreadyInUse

        fillValidForm()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_error_email_taken, viewModel.uiState.value.emailErrorRes)
        assertEquals(0, events.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `una contrasena debil se rechaza indicando el requisito sin tocar la red`() = runTest {
        // RF-07 criterio 3
        viewModel.onEmailChange("usuaria@correo.co")
        viewModel.onPasswordChange("corta7c")
        viewModel.onConfirmPasswordChange("corta7c")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_error_password_short, viewModel.uiState.value.passwordErrorRes)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun `un correo mal formado muestra el mensaje de formato`() = runTest {
        viewModel.onEmailChange("sin-arroba")
        viewModel.onPasswordChange("clave-segura-1")
        viewModel.onConfirmPasswordChange("clave-segura-1")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_error_email_format, viewModel.uiState.value.emailErrorRes)
    }

    @Test
    fun `un fallo del proveedor muestra el error generico`() = runTest {
        // Mensaje genérico que no revela el propósito (ARQUITECTURA §3)
        repository.nextCreateResult = CreateAccountResult.GenericFailure

        fillValidForm()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.generalErrorRes)
    }

    @Test
    fun `mientras se envia hay estado de carga y no se admite reenvio`() = runTest {
        repository.gate = CompletableDeferred()

        fillValidForm()
        viewModel.onSubmit()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onSubmit() // ignorado: ya hay un envío en curso
        repository.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.createCalls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `escribir de nuevo limpia el error del campo`() = runTest {
        repository.nextCreateResult = CreateAccountResult.EmailAlreadyInUse
        fillValidForm()
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onEmailChange("otra@correo.co")

        assertNull(viewModel.uiState.value.emailErrorRes)
    }

    @Test
    fun `sin campos completos no se puede enviar`() = runTest {
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(0, repository.createCalls)
    }
}

// El fake compartido vive en accounts/domain/usecase/AuthFakes.kt.
