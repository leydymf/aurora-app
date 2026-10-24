package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.core.security.FakeAccessCodePresence
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscardUnfinishedActivationUseCaseTest {
    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val accessCode = FakeAccessCodePresence(codeExists = false)
    private val useCase = DiscardUnfinishedActivationUseCase(accessCode, repository, credentials)

    private fun leaveUnfinishedRegistration() {
        repository.activeSession = true
        credentials.save("usuaria@correo.co", "clave-segura-1")
    }

    @Test
    fun `sin codigo de acceso descarta la sesion y el blob`() = runTest {
        // Hallazgo A (v0.3.2)
        leaveUnfinishedRegistration()

        useCase()

        assertNull(credentials.stored)
        assertFalse(repository.activeSession)
        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun `con codigo de acceso no toca nada`() = runTest {
        // Guarda: es el reingreso de una usuaria activa (P-31 C).
        accessCode.codeExists = true
        leaveUnfinishedRegistration()

        useCase()

        assertNotNull(credentials.stored)
        assertTrue(repository.activeSession)
        assertEquals(0, credentials.eraseCalls)
        assertEquals(0, repository.signOutCalls)
    }

    @Test
    fun `si no se puede leer el codigo no toca nada`() = runTest {
        // Ante la duda no se borra: borrar a una usuaria activa la expulsaría.
        accessCode.failOnRead = true
        leaveUnfinishedRegistration()

        useCase()

        assertNotNull(credentials.stored)
        assertTrue(repository.activeSession)
    }

    @Test
    fun `un descarte tardio nunca corre despues de crear la cuenta nueva`() = runTest {
        // El consentimiento lanza el descarte sin esperarlo; si quedara
        // suspendido y la creación de cuenta pasara por delante, al reanudarse
        // cerraría la sesión y borraría el blob de la cuenta RECIÉN creada. El
        // mutex del singleton lo impide: sin él, esta prueba falla.
        val callLog = mutableListOf<String>()
        repository.callLog = callLog
        leaveUnfinishedRegistration()
        val gate = CompletableDeferred<Unit>()
        accessCode.gateNextRead = gate
        val createAccount = CreateAccountUseCase(repository, FakeSyncKeyManager(), credentials, useCase)

        val consentDiscard = launch { useCase() }
        runCurrent()
        val creation = async { createAccount("cuenta-b@correo.co", "clave-cuenta-b") }
        runCurrent()
        gate.complete(Unit)
        consentDiscard.join()
        creation.await()

        assertEquals("createAccount", callLog.last())
        assertEquals("cuenta-b@correo.co", credentials.stored?.email)
    }

    @Test
    fun `sin restos es inocuo`() = runTest {
        useCase()

        assertNull(credentials.stored)
        assertFalse(repository.activeSession)
    }
}
