package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.PasswordResetResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restablecimiento por correo (RF-46).
 *
 * Lo que de verdad se fija aquí es que la pantalla **no sirva para averiguar
 * quién tiene cuenta**: el desenlace es idéntico con un correo que existe y con
 * uno que no, y también cuando se agotan los intentos. Lo segundo es la pausa
 * temporal, que evita inundar de correos la bandeja de una mujer —un correo
 * repetido en su bandeja es un rastro fuera del camuflaje.
 */
class SendPasswordResetUseCaseTest {
    private val repository = FakeAuthRepository()
    private val attempts = FakeSignInAttemptStore()
    private val clock = MutableWallClock()
    private val useCase = SendPasswordResetUseCase(repository, attempts, clock)

    @Test
    fun `un correo con cuenta recibe el mensaje unico`() = runTest {
        assertEquals(PasswordResetResult.Requested, useCase("usuaria@correo.co"))
        assertEquals(listOf("usuaria@correo.co"), repository.passwordResetRequests)
    }

    @Test
    fun `un correo SIN cuenta recibe exactamente el mismo desenlace`() = runTest {
        // El repositorio ya convierte «no encontrado» en `Requested`; aquí se
        // fija que el caso de uso no vuelve a mirar ni distingue.
        repository.nextPasswordResetResult = PasswordResetResult.Requested

        assertEquals(PasswordResetResult.Requested, useCase("no.existe@correo.co"))
    }

    @Test
    fun `un correo mal formado tampoco se distingue`() = runTest {
        assertEquals(PasswordResetResult.Requested, useCase("esto no es un correo"))
    }

    @Test
    fun `el correo se envia sin espacios alrededor`() = runTest {
        useCase("  usuaria@correo.co  ")

        assertEquals(listOf("usuaria@correo.co"), repository.passwordResetRequests)
    }

    @Test
    fun `sin conexion se dice que el correo no salio`() = runTest {
        // Esto SÍ se distingue: si no, la usuaria esperaría un mensaje que
        // nunca va a llegar (mismo criterio que RF-20).
        repository.nextPasswordResetResult = PasswordResetResult.NoConnection

        assertEquals(PasswordResetResult.NoConnection, useCase("usuaria@correo.co"))
    }

    @Test
    fun `el quinto envio seguido activa la pausa temporal`() = runTest {
        repeat(4) { assertEquals(PasswordResetResult.Requested, useCase("usuaria@correo.co")) }

        // El quinto se entrega igual —el mensaje no cambia— pero deja la pausa.
        assertEquals(PasswordResetResult.Requested, useCase("usuaria@correo.co"))
        assertNotNull(attempts.stored.lockDeadlineEpochMs)
    }

    @Test
    fun `durante la pausa no se consulta al proveedor y el mensaje no cambia`() = runTest {
        repeat(5) { useCase("usuaria@correo.co") }
        val sentBefore = repository.passwordResetRequests.size

        val outcome = useCase("usuaria@correo.co")

        assertTrue(outcome is PasswordResetResult.Locked)
        assertEquals(sentBefore, repository.passwordResetRequests.size)
    }

    @Test
    fun `al expirar la pausa se puede volver a pedir`() = runTest {
        repeat(5) { useCase("usuaria@correo.co") }
        clock.nowMs += SignInUseCase.LOCK_DURATION_MS + 1

        assertEquals(PasswordResetResult.Requested, useCase("usuaria@correo.co"))
    }

    @Test
    fun `un fallo de red no gasta intentos`() = runTest {
        repository.nextPasswordResetResult = PasswordResetResult.NoConnection

        repeat(5) { useCase("usuaria@correo.co") }

        assertEquals(0, attempts.stored.failedCount)
        assertEquals(null, attempts.stored.lockDeadlineEpochMs)
    }

    @Test
    fun `el contador del restablecimiento es propio y no bloquea el inicio de sesion`() = runTest {
        // Almacenes distintos: abusar del restablecimiento no debe cerrarle la
        // entrada a la usuaria. Aquí se comprueba que este caso de uso solo
        // escribe en el suyo.
        val loginAttempts = FakeSignInAttemptStore()
        val signIn = SignInUseCase(
            repository,
            loginAttempts,
            clock,
            com.auroraclima.app.core.security.FakeSyncKeyManager(),
            NoCloudReconciliation,
            emptySet(),
            FakeCredentialStore()
        )

        repeat(5) { useCase("usuaria@correo.co") }

        assertEquals(0, loginAttempts.stored.failedCount)
        assertEquals(0L, signIn.currentLockMillis())
    }
}
