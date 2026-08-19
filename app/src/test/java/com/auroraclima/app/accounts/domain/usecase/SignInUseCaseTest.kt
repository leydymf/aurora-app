package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.FakeSyncKeyManager
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
    private val syncKeys = FakeSyncKeyManager()
    private val credentials = FakeCredentialStore()
    private var restoreCalls = 0
    private val restorer = AccountDataRestorer { restoreCalls++ }
    private val useCase = SignInUseCase(repository, store, clock, syncKeys, setOf(restorer), credentials)

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

    @Test
    fun `el exito establece la llave del respaldo y dispara los restauradores`() = runTest {
        // RF-39: el login es el único momento con la contraseña en memoria.
        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))

        assertEquals(listOf("clave-segura-1"), syncKeys.establishedPasswords)
        assertEquals(1, restoreCalls)
    }

    @Test
    fun `un rechazo no toca llaves ni restauradores`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials

        useCase("usuaria@correo.co", "clave-mala")

        assertEquals(0, syncKeys.establishedPasswords.size)
        assertEquals(0, restoreCalls)
    }

    @Test
    fun `sin llave establecida no corren los restauradores`() = runTest {
        // Sin DEK no habría cómo abrir lo descargado: el intento sería ruido.
        syncKeys.establishOutcome = Result.failure(IllegalStateException())

        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))

        assertEquals(0, restoreCalls)
    }

    @Test
    fun `un fallo del respaldo jamas degrada el acceso concedido`() = runTest {
        // Mejor esfuerzo (§3): el siguiente inicio de sesión reintenta.
        syncKeys.establishOutcome = Result.failure(IllegalStateException())

        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))
    }

    @Test
    fun `el exito hace nacer el blob de credenciales`() = runTest {
        // P-31 opción C: el login manual es donde nace el reingreso silencioso.
        useCase("usuaria@correo.co", "clave-segura-1")

        assertEquals("usuaria@correo.co", credentials.stored?.email)
        assertEquals("clave-segura-1", credentials.stored?.password)
    }

    @Test
    fun `un rechazo no crea el blob`() = runTest {
        repository.nextSignInResult = SignInResult.InvalidCredentials

        useCase("usuaria@correo.co", "clave-mala")

        assertNull(credentials.stored)
    }

    @Test
    fun `un fallo al guardar el blob jamas degrada el acceso concedido`() = runTest {
        // Mejor esfuerzo: sin blob solo se pierde el silencioso, no la entrada.
        credentials.failNextSave = true

        assertEquals(SignInOutcome.Success, useCase("usuaria@correo.co", "clave-segura-1"))
        assertNull(credentials.stored)
        // El respaldo (RF-39) corre igual: los pasos de mejor esfuerzo no se encadenan.
        assertEquals(listOf("clave-segura-1"), syncKeys.establishedPasswords)
    }
}
