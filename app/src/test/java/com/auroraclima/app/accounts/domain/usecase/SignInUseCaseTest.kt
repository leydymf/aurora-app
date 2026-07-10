package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.SignInResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    private val repository = FakeAuthRepository()
    private val store = FakeSignInAttemptStore()
    private val clock = MutableWallClock()
    private val useCase = SignInUseCase(repository, store, clock)

    @Test
    fun `credenciales correctas dan acceso`() = runTest {
        // RF-15 criterio 1
        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))
    }

    @Test
    fun `un rechazo de credenciales devuelve el resultado generico y cuenta el intento`() = runTest {
        // RF-15 criterio 2
        repository.nextSignInResult = SignInResult.InvalidCredentials

        assertEquals(SignInOutcome.RejectedCredentials, useCase("usuaria@correo.co", "clave-mala"))
        assertEquals(1, store.stored.failedCount)
    }

    @Test
    fun `el quinto intento fallido consecutivo activa el bloqueo`() = runTest {
        // RF-15 criterio 3
        repository.nextSignInResult = SignInResult.InvalidCredentials

        repeat(4) {
            assertEquals(SignInOutcome.RejectedCredentials, useCase("usuaria@correo.co", "clave-mala"))
        }
        val fifth = useCase("usuaria@correo.co", "clave-mala")

        assertTrue(fifth is SignInOutcome.Locked)
        assertEquals(SignInUseCase.LOCK_DURATION_MS, (fifth as SignInOutcome.Locked).remainingMillis)
        assertNotNull(store.stored.lockDeadlineEpochMs)
    }

    @Test
    fun `durante el bloqueo ni se consulta el proveedor`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repeat(5) { useCase("usuaria@correo.co", "clave-mala") }
        val callsAtLock = repository.signInCalls

        clock.nowMs += 60_000
        val outcome = useCase("usuaria@correo.co", "clave-buena")

        assertTrue(outcome is SignInOutcome.Locked)
        assertEquals(SignInUseCase.LOCK_DURATION_MS - 60_000, (outcome as SignInOutcome.Locked).remainingMillis)
        assertEquals(callsAtLock, repository.signInCalls)
    }

    @Test
    fun `al expirar el bloqueo se puede intentar de nuevo`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repeat(5) { useCase("usuaria@correo.co", "clave-mala") }

        clock.nowMs += SignInUseCase.LOCK_DURATION_MS
        repository.nextSignInResult = SignInResult.Success

        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))
        // El éxito limpia contador y fecha límite
        assertEquals(0, store.stored.failedCount)
        assertNull(store.stored.lockDeadlineEpochMs)
    }

    @Test
    fun `el exito reinicia el contador de intentos`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repeat(4) { useCase("usuaria@correo.co", "clave-mala") }

        repository.nextSignInResult = SignInResult.Success
        useCase("usuaria@correo.co", "clave-segura-1")

        assertEquals(0, store.stored.failedCount)
        // Cuatro fallos nuevos no bloquean: la cuenta empezó de cero
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repeat(4) {
            assertEquals(SignInOutcome.RejectedCredentials, useCase("usuaria@correo.co", "clave-mala"))
        }
    }

    @Test
    fun `un fallo de red no castiga a la usuaria`() = runTest {
        repository.nextSignInResult = SignInResult.GenericFailure

        repeat(5) {
            assertEquals(SignInOutcome.Failure, useCase("usuaria@correo.co", "clave-segura-1"))
        }
        assertEquals(0, store.stored.failedCount)
        assertNull(store.stored.lockDeadlineEpochMs)
    }

    @Test
    fun `el bloqueo vigente se consulta sin intentar`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials
        repeat(5) { useCase("usuaria@correo.co", "clave-mala") }

        clock.nowMs += 2 * 60_000

        assertEquals(SignInUseCase.LOCK_DURATION_MS - 2 * 60_000, useCase.currentLockMillis())
    }

    @Test
    fun `sin bloqueo el restante es cero`() = runTest {
        assertEquals(0, useCase.currentLockMillis())
    }
}
