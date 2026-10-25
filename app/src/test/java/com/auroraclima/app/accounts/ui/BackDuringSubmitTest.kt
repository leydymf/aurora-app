package com.auroraclima.app.accounts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.security.WallClock
import com.auroraclima.app.accounts.domain.usecase.CreateAccountUseCase
import com.auroraclima.app.accounts.domain.usecase.DiscardUnfinishedActivationUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.FakeSignInAttemptStore
import com.auroraclima.app.accounts.domain.usecase.FakeSilentSessionOrchestrator
import com.auroraclima.app.accounts.domain.usecase.NoCloudReconciliation
import com.auroraclima.app.accounts.domain.usecase.SendPasswordResetUseCase
import com.auroraclima.app.accounts.domain.usecase.SignInUseCase
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeAccessCodePresence
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.KeyEstablishment
import com.auroraclima.app.core.security.SyncKeyManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * P-123 (v0.3.2, prompt 3): volver (flecha o gesto atrás) MIENTRAS se crea la cuenta
 * o se inicia sesión en el flujo de activación. El pop destruye el ViewModel y cancela
 * la corrutina, pero el proveedor termina igual —Firebase no cancela la petición ya
 * enviada— y puede dejar la sesión iniciada y, según dónde se corte, el blob de
 * credenciales guardado.
 *
 * Lo que se fija: ese estado a medias NO permite entrar sin contraseña. Sin código de
 * acceso (todavía no se creó) la única puerta es el flujo de activación, y ese login
 * pide la contraseña aunque la sesión siga viva o haya blob, y además los descarta
 * (hallazgo A del prompt 2). El reingreso con sesión viva solo existe con código.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackDuringSubmitTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val accessCode = FakeAccessCodePresence(codeExists = false)
    private val discardUnfinished =
        DiscardUnfinishedActivationUseCase(accessCode, repository, credentials, NoCloudReconciliation, emptySet())

    @Test
    fun `volver mientras el proveedor crea la cuenta no deja entrar sin contrasena`() = runTest {
        repository.gate = CompletableDeferred()
        val createAccount = createAccountViewModel(FakeSyncKeyManager())
        createAccount.submitValidForm()
        runCurrent()
        assertTrue(createAccount.uiState.value.isLoading)

        createAccount.destroy()
        providerFinishesAnyway()

        // Cancelada en la espera del proveedor: el blob no llegó a nacer.
        assertNull(credentials.stored)
        assertActivationLoginAsksForPassword()
    }

    @Test
    fun `volver cuando la cuenta ya existe y se publica el sobre de llaves no deja entrar sin contrasena`() = runTest {
        // El peor corte: el proveedor ya respondió, el blob ya se guardó y la
        // corrutina muere publicando el sobre de llaves (RF-39).
        val establishing = CompletableDeferred<Unit>()
        val createAccount = createAccountViewModel(BlockingEstablish(establishing))
        createAccount.submitValidForm()
        runCurrent()
        repository.activeSession = true
        assertNotNull("el blob ya está guardado en este corte", credentials.stored)

        createAccount.destroy()
        establishing.complete(Unit)
        advanceUntilIdle()

        assertActivationLoginAsksForPassword()
    }

    @Test
    fun `volver mientras se inicia sesion en la activacion no deja entrar sin contrasena`() = runTest {
        repository.gate = CompletableDeferred()
        val login = activationLoginViewModel()
        runCurrent()
        login.onEmailChange("usuaria@correo.co")
        login.onPasswordChange("clave-segura-1")
        login.onSubmit()
        runCurrent()
        assertTrue(login.uiState.value.isLoading)

        login.destroy()
        providerFinishesAnyway()

        assertNull(credentials.stored)
        assertActivationLoginAsksForPassword()
    }

    /**
     * El siguiente login de activación —el único camino sin código— no abre solo:
     * muestra el formulario, no llama al proveedor y deja sin sesión ni blob lo que
     * quedó a medias. Y sigue sin haber código de acceso.
     */
    private fun TestScope.assertActivationLoginAsksForPassword() {
        repository.gate = null
        val signInsBefore = repository.signInCalls
        val next = activationLoginViewModel()
        val opened = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { next.openEvents.toList(opened) }
        advanceUntilIdle()

        assertEquals("el login de activación no debe abrir sin contraseña", 0, opened.size)
        assertFalse(next.uiState.value.isCheckingSession)
        assertEquals(signInsBefore, repository.signInCalls)
        assertFalse("la sesión a medias se cierra", repository.activeSession)
        assertNull("el blob a medias se borra", credentials.stored)
        assertFalse(accessCode.codeExists)
    }

    /** Firebase completa la petición aunque la corrutina ya no espere: deja la sesión. */
    private fun TestScope.providerFinishesAnyway() {
        repository.activeSession = true
        repository.gate?.complete(Unit)
        advanceUntilIdle()
    }

    private fun createAccountViewModel(syncKeys: SyncKeyManager) = CreateAccountViewModel(
        CreateAccountUseCase(repository, syncKeys, credentials, discardUnfinished)
    )

    private fun CreateAccountViewModel.submitValidForm() {
        onEmailChange("usuaria@correo.co")
        onPasswordChange("clave-segura-1")
        onConfirmPasswordChange("clave-segura-1")
        onSubmit()
    }

    private fun TestScope.activationLoginViewModel(): LoginViewModel {
        val clock = WallClock { BASE_EPOCH_MS + testScheduler.currentTime }
        repository.nextSignInResult = SignInResult.Success
        repository.nextCreateResult = CreateAccountResult.Success
        return LoginViewModel(
            SavedStateHandle(mapOf(LOGIN_FLOW_ARG to LOGIN_FLOW_FIRST_USE)),
            SignInUseCase(
                repository,
                FakeSignInAttemptStore(),
                clock,
                FakeSyncKeyManager(),
                NoCloudReconciliation,
                emptySet(),
                credentials
            ),
            repository,
            credentials,
            FakeSilentSessionOrchestrator(),
            SendPasswordResetUseCase(repository, FakeSignInAttemptStore(), clock),
            accessCode,
            discardUnfinished
        )
    }

    /** El pop de la ruta: destruye el ViewModel y cancela su corrutina en curso. */
    private fun ViewModel.destroy() {
        ViewModelStore().apply {
            put("pop", this@destroy)
            clear()
        }
    }

    /** Sobre de llaves que no termina hasta que la prueba lo suelta. */
    private class BlockingEstablish(private val release: CompletableDeferred<Unit>) :
        SyncKeyManager by FakeSyncKeyManager() {
        override suspend fun establish(password: String): Result<KeyEstablishment> {
            release.await()
            return Result.success(KeyEstablishment.Ready)
        }
    }

    private companion object {
        const val BASE_EPOCH_MS = 1_700_000_000_000L
    }
}
