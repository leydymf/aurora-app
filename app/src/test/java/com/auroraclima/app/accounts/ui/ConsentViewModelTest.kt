package com.auroraclima.app.accounts.ui

import com.auroraclima.app.accounts.domain.usecase.CreateAccountUseCase
import com.auroraclima.app.accounts.domain.usecase.DiscardUnfinishedActivationUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.NoCloudReconciliation
import com.auroraclima.app.accounts.domain.usecase.RecordingLocalEraser
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeAccessCodePresence
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val accessCode = FakeAccessCodePresence(codeExists = false)
    private val callLog = mutableListOf<String>()

    /** Los registros que la cuenta A restauró antes de crear el código (hallazgo A′). */
    private val accountAData = RecordingLocalEraser(callLog, "datos-de-A")
    private val discardUnfinished =
        DiscardUnfinishedActivationUseCase(
            accessCode,
            repository,
            credentials,
            NoCloudReconciliation,
            setOf(accountAData)
        )

    @Test
    fun `la casilla nace sin preseleccionar`() {
        // RF-17 criterio 1: sin casillas preseleccionadas
        assertFalse(ConsentViewModel().uiState.value.isAccepted)
    }

    @Test
    fun `marcar y desmarcar la casilla actualiza el estado`() {
        val viewModel = ConsentViewModel()
        viewModel.onAcceptanceChange(true)
        assertTrue(viewModel.uiState.value.isAccepted)

        viewModel.onAcceptanceChange(false)
        assertFalse(viewModel.uiState.value.isAccepted)
    }

    @Test
    fun `consentimiento y cuenta B nueva sobre los restos de la cuenta A`() = runTest {
        // Camino completo del atacante: código de activación → bienvenida →
        // consentimiento → «crear cuenta» con otra cuenta. B no hereda la sesión, el blob ni los
        // datos locales de A.
        repository.callLog = callLog
        repository.activeSession = true
        credentials.save("cuenta-a@correo.co", "clave-cuenta-a")
        WelcomeViewModel(discardUnfinished)
        val consent = ConsentViewModel()
        consent.onAcceptanceChange(true)
        val createAccount = CreateAccountViewModel(
            CreateAccountUseCase(repository, FakeSyncKeyManager(), credentials, discardUnfinished)
        )

        createAccount.onEmailChange("cuenta-b@correo.co")
        createAccount.onPasswordChange("clave-cuenta-b")
        createAccount.onConfirmPasswordChange("clave-cuenta-b")
        createAccount.onSubmit()
        advanceUntilIdle()

        // La sesión y los datos de A caen antes de que el proveedor cree a B.
        assertEquals("createAccount", callLog.last())
        assertTrue(callLog.indexOf("signOut") in 0 until callLog.indexOf("createAccount"))
        assertTrue(callLog.indexOf("datos-de-A") in 0 until callLog.indexOf("createAccount"))
        assertEquals("cuenta-b@correo.co", credentials.stored?.email)
    }
}
