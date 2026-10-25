package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.core.security.FakeAccessCodePresence
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.LocalAccountDataEraser
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

    /** Bitácora de los borradores locales: cuáles corrieron y en qué orden. */
    private val erased = mutableListOf<String>()

    // Los tres que se conservan llevan los nombres de los borradores reales
    // que declaran la excepción (acceso, intentos y caché de la nube).
    private val localErasers = setOf(
        RecordingLocalEraser(erased, "registros"),
        RecordingLocalEraser(erased, "llave-del-respaldo"),
        RecordingLocalEraser(erased, "codigos", keptOnUnfinishedActivation = true),
        RecordingLocalEraser(erased, "intentos", keptOnUnfinishedActivation = true),
        RecordingLocalEraser(erased, "cache-de-la-nube", keptOnUnfinishedActivation = true)
    )
    private val reconciliation = FakeCloudReconciliation().apply { callLog = erased }
    private val useCase =
        DiscardUnfinishedActivationUseCase(accessCode, repository, credentials, reconciliation, localErasers)

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
    fun `sin codigo de acceso borra los datos de cuenta y conserva los exentos`() = runTest {
        // Hallazgo A′ (v0.3.2): lo restaurado antes del código cae; la puerta,
        // el bloqueo y la caché de la nube (por su causa declarada) no.
        leaveUnfinishedRegistration()

        useCase()

        // La reconciliación de la otra cuenta se detiene ANTES de borrar.
        assertEquals("stopReconciliation", erased.first())
        assertEquals(setOf("stopReconciliation", "registros", "llave-del-respaldo"), erased.toSet())
    }

    @Test
    fun `cancelar a quien lo pidio no deja el borrado a medias`() = runTest {
        // El pop del consentimiento cancela su scope; los almacenes no pueden
        // quedar la mitad borrados y la otra mitad vivos.
        val gate = CompletableDeferred<Unit>()
        val slow = LocalAccountDataEraser {
            gate.await()
            erased += "lento"
        }
        val useCase = DiscardUnfinishedActivationUseCase(
            accessCode,
            repository,
            credentials,
            NoCloudReconciliation,
            linkedSetOf(slow, RecordingLocalEraser(erased, "registros"))
        )

        val caller = launch { useCase() }
        runCurrent()
        caller.cancel()
        gate.complete(Unit)
        caller.join()

        assertEquals(listOf("lento", "registros"), erased)
    }

    @Test
    fun `un borrador que falla no frena a los demas`() = runTest {
        val failing = LocalAccountDataEraser { throw IllegalStateException() }
        val useCase = DiscardUnfinishedActivationUseCase(
            accessCode,
            repository,
            credentials,
            NoCloudReconciliation,
            setOf(failing, RecordingLocalEraser(erased, "registros"))
        )

        useCase()

        assertEquals(listOf("registros"), erased)
    }

    @Test
    fun `con codigo de acceso no corre ningun borrador`() = runTest {
        accessCode.codeExists = true

        useCase()

        assertTrue(erased.isEmpty())
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
