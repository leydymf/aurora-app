package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reingreso silencioso (P-31 opción C): confirma la sesión con el blob, sin
 * tocar el contador de RF-15; el rechazo definitivo destruye el blob y los
 * fallos transitorios lo conservan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentSignInUseCaseTest {
    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val syncKeys = FakeSyncKeyManager()
    private var restoreCalls = 0
    private val restorer = AccountDataRestorer { restoreCalls++ }
    private val cloudReconciliation = FakeCloudReconciliation()
    private val useCase =
        SilentSignInUseCase(repository, credentials, syncKeys, cloudReconciliation, setOf(restorer))

    private fun givenStoredCredentials() {
        credentials.save("usuaria@correo.co", "clave-segura-1")
    }

    @Test
    fun `con blob y proveedor de acuerdo la sesion se confirma con las credenciales guardadas`() = runTest {
        givenStoredCredentials()

        assertEquals(SilentSignInOutcome.Success, useCase())
        assertEquals(listOf("usuaria@correo.co" to "clave-segura-1"), repository.signInRequests)
    }

    @Test
    fun `el exito corre los mismos pasos del login manual`() = runTest {
        // RF-39: establecer la llave y disparar los restauradores — así la cola
        // pendiente de una sesión offline se re-sube sola.
        givenStoredCredentials()

        useCase()

        assertEquals(listOf("clave-segura-1"), syncKeys.establishedPasswords)
        assertEquals(1, restoreCalls)
    }

    @Test
    fun `sin blob no se consulta el proveedor`() = runTest {
        assertEquals(SilentSignInOutcome.NoCredentials, useCase())
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `el rechazo definitivo destruye el blob`() = runTest {
        // Contraseña cambiada en otro dispositivo o credencial revocada: el
        // estado obsoleto no debe sobrevivir ni reintentar.
        givenStoredCredentials()
        repository.nextSignInResult = SignInResult.InvalidCredentials

        assertEquals(SilentSignInOutcome.Rejected, useCase())
        assertNull(credentials.stored)
    }

    @Test
    fun `un fallo transitorio conserva el blob`() = runTest {
        // Sin red o servicio caído: se opera en local y se reintenta después.
        givenStoredCredentials()
        repository.nextSignInResult = SignInResult.GenericFailure

        assertEquals(SilentSignInOutcome.Unavailable, useCase())
        assertNotNull(credentials.stored)
        assertEquals(0, credentials.eraseCalls)
    }

    @Test
    fun `un rechazo no toca llaves ni restauradores`() = runTest {
        givenStoredCredentials()
        repository.nextSignInResult = SignInResult.InvalidCredentials

        useCase()

        assertEquals(0, syncKeys.establishedPasswords.size)
        assertEquals(0, restoreCalls)
    }

    @Test
    fun `un fallo del respaldo jamas degrada la confirmacion concedida`() = runTest {
        givenStoredCredentials()
        syncKeys.establishOutcome = Result.failure(IllegalStateException())

        assertEquals(SilentSignInOutcome.Success, useCase())
        assertEquals(0, restoreCalls)
    }

    @Test
    fun `un login que culmina tras el cierre de sesion se deshace con signOut`() = runTest {
        // Auditoría A-1 (H-3): el Task del SDK no se aborta con la cancelación;
        // si la salida rápida llega mientras el proveedor responde, la sesión
        // recién persistida se cierra para no dejar el rastro tras la limpieza.
        givenStoredCredentials()
        repository.gate = CompletableDeferred()
        val job = launch { useCase() }
        runCurrent()

        job.cancel()
        repository.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.signOutCalls)
        // Nada más corre tras la cancelación: ni llaves ni restauradores.
        assertEquals(0, syncKeys.establishedPasswords.size)
        assertEquals(0, restoreCalls)
        // El blob se conserva: el siguiente reingreso confirma de nuevo.
        assertNotNull(credentials.stored)
    }

    @Test
    fun `un rechazo que culmina tras el cierre de sesion destruye el blob igual`() = runTest {
        // La credencial demostró ser inválida: no debe sobrevivir aunque la
        // sesión interna ya se haya cerrado.
        givenStoredCredentials()
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repository.gate = CompletableDeferred()
        val job = launch { useCase() }
        runCurrent()

        job.cancel()
        repository.gate?.complete(Unit)
        advanceUntilIdle()

        assertNull(credentials.stored)
        assertEquals(0, repository.signOutCalls)
    }
}
