package com.auroraclima.app.accounts.ui

import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.usecase.ChangePasswordUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Cambio de contraseña (RF-39): validación local de coincidencia, errores
 * genéricos por campo (criterio de RF-15) e higiene del estado al culminar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val syncKeys = FakeSyncKeyManager()
    private val viewModel = ChangePasswordViewModel(ChangePasswordUseCase(repository, syncKeys, FakeCredentialStore()))

    private fun fillValidForm() {
        viewModel.onCurrentPasswordChange("clave-actual-1")
        viewModel.onNewPasswordChange("clave-nueva-22")
        viewModel.onConfirmPasswordChange("clave-nueva-22")
    }

    private fun TestScope.collectDoneEvents(): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.doneEvents.toList(events)
        }
        return events
    }

    @Test
    fun `el exito limpia las contrasenas y emite el evento`() = runTest {
        val events = collectDoneEvents()
        fillValidForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertEquals(listOf(Unit), events)
    }

    @Test
    fun `una confirmacion distinta se rechaza sin tocar la red`() = runTest {
        viewModel.onCurrentPasswordChange("clave-actual-1")
        viewModel.onNewPasswordChange("clave-nueva-22")
        viewModel.onConfirmPasswordChange("clave-nueva-23")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_error_password_mismatch, viewModel.uiState.value.confirmErrorRes)
        assertEquals(0, repository.reauthenticateCalls)
    }

    @Test
    fun `una credencial rechazada marca el campo actual con el error generico`() = runTest {
        repository.nextReauthenticateResult = ReauthenticateResult.InvalidCredentials
        fillValidForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_login_error, viewModel.uiState.value.currentErrorRes)
    }

    @Test
    fun `sin conexion se afirma que nada cambio`() = runTest {
        repository.nextReauthenticateResult = ReauthenticateResult.NoConnection
        fillValidForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.account_password_change_offline, viewModel.uiState.value.generalErrorRes)
        assertTrue(repository.updatedPasswords.isEmpty())
    }
}
