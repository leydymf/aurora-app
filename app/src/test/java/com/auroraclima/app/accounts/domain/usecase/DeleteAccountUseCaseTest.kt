package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.DeleteAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.PendingErasureFlag
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El corazón de RF-20 es el ORDEN del borrado y qué queda si falla a mitad
 * (decisión de sesión aprobada 2026-07-20): re-auth → nube → cuenta → local.
 * Un fallo antes de borrar la cuenta detiene TODO con lo local intacto (el
 * reintento es idempotente); después de borrarla, lo local se borra a mejor
 * esfuerzo aunque un eraser falle.
 */
class DeleteAccountUseCaseTest {
    private val callLog = mutableListOf<String>()

    private val authRepository = FakeAuthRepository().apply { callLog = this@DeleteAccountUseCaseTest.callLog }

    private class FakeRemoteEraser(
        private val log: MutableList<String>,
        private val name: String = "remote"
    ) : RemoteAccountDataEraser {
        var nextResult: Result<Unit> = Result.success(Unit)
        var calls = 0
            private set

        override suspend fun erase(): Result<Unit> {
            calls++
            log += name
            return nextResult
        }
    }

    private class FakeLocalEraser(
        private val log: MutableList<String>,
        private val name: String,
        private val failure: Exception? = null
    ) : LocalAccountDataEraser {
        var calls = 0
            private set

        override suspend fun erase() {
            calls++
            log += name
            failure?.let { throw it }
        }
    }

    /** La marca durable de H-1: graba en la bitácora cuándo se iza y se retira. */
    private class FakePendingErasureFlag(private val log: MutableList<String>) : PendingErasureFlag {
        var raiseSucceeds = true
        var raised = false
            private set

        override suspend fun raise(): Boolean {
            log += "flag-raise"
            if (raiseSucceeds) raised = true
            return raiseSucceeds
        }

        override suspend fun isRaised(): Boolean = raised

        override suspend fun clear() {
            log += "flag-clear"
            raised = false
        }
    }

    private val remoteEraser = FakeRemoteEraser(callLog)
    private val localA = FakeLocalEraser(callLog, "local-a")
    private val localB = FakeLocalEraser(callLog, "local-b")
    private val pendingFlag = FakePendingErasureFlag(callLog)

    private fun useCase(
        remote: Set<RemoteAccountDataEraser> = setOf(remoteEraser),
        local: Set<LocalAccountDataEraser> = setOf(localA, localB)
    ) = DeleteAccountUseCase(authRepository, remote, local, pendingFlag)

    @Test
    fun `el camino feliz borra en el orden reauth, nube, marca, cuenta y local`() = runTest {
        // RF-20 criterios 2 y 3: la decisión central de la sesión. La marca de
        // H-1 se iza ANTES de borrar la cuenta y se retira solo al final.
        val result = useCase()("contrasena-1")

        assertEquals(DeleteAccountResult.Success, result)
        assertEquals(
            listOf("reauth", "remote", "flag-raise", "deleteUser", "local-a", "local-b", "flag-clear"),
            callLog
        )
        assertFalse(pendingFlag.raised)
    }

    @Test
    fun `si la marca durable no puede garantizarse se aborta antes de borrar la cuenta`() = runTest {
        // Auditoría H-1: sin red de seguridad en disco no se cruza el punto de
        // no retorno — nada se ha perdido y se puede reintentar.
        pendingFlag.raiseSucceeds = false

        val result = useCase()("contrasena-1")

        assertEquals(DeleteAccountResult.GenericFailure, result)
        assertEquals(listOf("reauth", "remote", "flag-raise"), callLog)
        assertEquals(0, authRepository.deleteCalls)
        assertEquals(0, localA.calls)
    }

    @Test
    fun `la contrasena incorrecta detiene todo sin tocar nada`() = runTest {
        // RF-20 criterio 1 (confirmación fuerte) + RF-15 criterio 2 (mensaje único).
        authRepository.nextReauthenticateResult = ReauthenticateResult.InvalidCredentials

        val result = useCase()("contrasena-mala")

        assertEquals(DeleteAccountResult.InvalidCredentials, result)
        assertEquals(listOf("reauth"), callLog)
        assertEquals(0, remoteEraser.calls)
        assertEquals(0, authRepository.deleteCalls)
        assertEquals(0, localA.calls)
    }

    @Test
    fun `sin red no se borra absolutamente nada`() = runTest {
        // La re-auth es la verificación temprana de red: sin conexión la UI puede
        // afirmar con honestidad que nada se ha eliminado.
        authRepository.nextReauthenticateResult = ReauthenticateResult.NoConnection

        val result = useCase()("contrasena-1")

        assertEquals(DeleteAccountResult.NoConnection, result)
        assertEquals(listOf("reauth"), callLog)
        assertEquals(0, remoteEraser.calls)
        assertEquals(0, authRepository.deleteCalls)
        assertEquals(0, localA.calls)
        assertEquals(0, localB.calls)
    }

    @Test
    fun `el fallo de la purga remota detiene todo con lo local intacto`() = runTest {
        // Fallo a mitad ANTES del punto de no retorno: cuenta viva, datos locales
        // intactos, reintento posible (la purga es idempotente).
        remoteEraser.nextResult = Result.failure(IllegalStateException())

        val result = useCase()("contrasena-1")

        assertEquals(DeleteAccountResult.GenericFailure, result)
        assertEquals(listOf("reauth", "remote"), callLog)
        assertEquals(0, authRepository.deleteCalls)
        assertEquals(0, localA.calls)
        assertEquals(0, localB.calls)
    }

    @Test
    fun `el fallo al borrar la cuenta deja lo local intacto y retira la marca`() = runTest {
        // La cuenta sigue viva: la marca no puede quedar izada o el próximo
        // arranque borraría lo local de una cuenta existente.
        authRepository.nextDeleteResult = DeleteUserResult.GenericFailure

        val result = useCase()("contrasena-1")

        assertEquals(DeleteAccountResult.GenericFailure, result)
        assertEquals(listOf("reauth", "remote", "flag-raise", "deleteUser", "flag-clear"), callLog)
        assertEquals(0, localA.calls)
        assertEquals(0, localB.calls)
        assertFalse(pendingFlag.raised)
    }

    @Test
    fun `la red caida al borrar la cuenta se distingue del fallo generico`() = runTest {
        authRepository.nextDeleteResult = DeleteUserResult.NoConnection

        assertEquals(DeleteAccountResult.NoConnection, useCase()("contrasena-1"))
        assertEquals(0, localA.calls)
    }

    @Test
    fun `un eraser local que falla no detiene a los demas y deja la marca izada`() = runTest {
        // Tras borrar la cuenta no hay marcha atrás: todo lo local se intenta,
        // y lo que falle lo reintenta el arranque gracias a la marca (H-3).
        val failing = FakeLocalEraser(callLog, "local-falla", failure = IllegalStateException())

        val result = useCase(local = linkedSetOf(failing, localA, localB))("contrasena-1")

        assertEquals(DeleteAccountResult.Success, result)
        assertEquals(1, failing.calls)
        assertEquals(1, localA.calls)
        assertEquals(1, localB.calls)
        assertTrue(pendingFlag.raised)
    }

    @Test
    fun `el reintento tras una purga parcial vuelve a purgar la nube`() = runTest {
        // Idempotencia del fallo a mitad: el primer intento falla en la nube; el
        // segundo re-purga (borrar lo ya borrado es no-op) y completa el resto.
        remoteEraser.nextResult = Result.failure(IllegalStateException())
        val subject = useCase()
        assertEquals(DeleteAccountResult.GenericFailure, subject("contrasena-1"))

        remoteEraser.nextResult = Result.success(Unit)
        callLog.clear()

        assertEquals(DeleteAccountResult.Success, subject("contrasena-1"))
        assertEquals(2, remoteEraser.calls)
        assertEquals(
            listOf("reauth", "remote", "flag-raise", "deleteUser", "local-a", "local-b", "flag-clear"),
            callLog
        )
        assertTrue(authRepository.reauthenticateCalls == 2 && authRepository.deleteCalls == 1)
    }
}
