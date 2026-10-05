package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.model.SilentSessionState
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.SilentSignInUseCase
import com.auroraclima.app.core.network.ConnectivityMonitor
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Coordinador del reingreso silencioso (P-31 opción C): confirma en segundo
 * plano, reintenta al regresar la red (o con espera creciente acotada, P-81) y
 * se limpia con la sesión interna.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentSessionCoordinatorTest {
    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val connectivity = FakeConnectivityMonitor()

    private fun TestScope.createCoordinator(): SilentSessionCoordinator {
        val useCase = SilentSignInUseCase(repository, credentials, FakeSyncKeyManager(), emptySet())
        return SilentSessionCoordinator(useCase, connectivity, backgroundScope)
    }

    private fun TestScope.collectLoginRequests(coordinator: SilentSessionCoordinator): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.loginRequests.toList(requests)
        }
        return requests
    }

    @Test
    fun `el exito termina en SignedIn sin pedir login`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        val coordinator = createCoordinator()
        val requests = collectLoginRequests(coordinator)

        coordinator.start()
        runCurrent()

        assertEquals(SilentSessionState.SignedIn, coordinator.state.value)
        assertEquals(0, requests.size)
    }

    @Test
    fun `el rechazo definitivo pide el login manual`() = runTest {
        credentials.save("usuaria@correo.co", "clave-vieja-1")
        repository.nextSignInResult = SignInResult.InvalidCredentials
        val coordinator = createCoordinator()
        val requests = collectLoginRequests(coordinator)

        coordinator.start()
        runCurrent()

        assertEquals(SilentSessionState.LoginRequired, coordinator.state.value)
        assertEquals(1, requests.size)
    }

    @Test
    fun `un blob ilegible degrada a login manual sin crash`() = runTest {
        // load() ya lo destruyó: el coordinador solo constata la ausencia.
        val coordinator = createCoordinator()
        val requests = collectLoginRequests(coordinator)

        coordinator.start()
        runCurrent()

        assertEquals(SilentSessionState.LoginRequired, coordinator.state.value)
        assertEquals(1, requests.size)
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `sin red queda Offline y reintenta al volver la conexion`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        connectivity.online.value = false
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()

        coordinator.start()
        runCurrent()
        assertEquals(SilentSessionState.Offline, coordinator.state.value)

        repository.nextSignInResult = SignInResult.Success
        connectivity.online.value = true
        runCurrent()

        assertEquals(SilentSessionState.SignedIn, coordinator.state.value)
        assertEquals(2, repository.signInCalls)
    }

    @Test
    fun `un fallo con red viva reintenta con espera creciente y luego solo al volver la red`() = runTest {
        // Red «disponible» pero el servicio falla (p. ej. DNS aún no listo tras
        // reconectar, P-81): tres reintentos a 5 s, 15 s y 45 s; después, solo
        // el regreso de la red vuelve a intentar — sin martillear un servicio caído.
        credentials.save("usuaria@correo.co", "clave-segura-1")
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()

        coordinator.start()
        runCurrent()
        assertEquals(SilentSessionState.Offline, coordinator.state.value)
        assertEquals(1, repository.signInCalls)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, repository.signInCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.signInCalls)
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(3, repository.signInCalls)
        advanceTimeBy(45_000)
        runCurrent()
        assertEquals(4, repository.signInCalls)
        assertEquals(SilentSessionState.Offline, coordinator.state.value)

        // Esperas agotadas: el tiempo ya no reintenta.
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(4, repository.signInCalls)

        repository.nextSignInResult = SignInResult.Success
        connectivity.online.value = false
        runCurrent()
        assertEquals(4, repository.signInCalls)
        connectivity.online.value = true
        runCurrent()
        assertEquals(SilentSessionState.SignedIn, coordinator.state.value)
        assertEquals(5, repository.signInCalls)
    }

    @Test
    fun `tras agotar las esperas un regreso de red rearma las esperas`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()

        coordinator.start()
        runCurrent()
        advanceTimeBy(65_000)
        runCurrent()
        assertEquals(4, repository.signInCalls)

        // La red vuelve de verdad: un intento inmediato (falla: DNS aún no listo)…
        connectivity.online.value = false
        runCurrent()
        connectivity.online.value = true
        runCurrent()
        assertEquals(5, repository.signInCalls)
        // …y las esperas vuelven a contar desde 5 s.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(6, repository.signInCalls)
    }

    @Test
    fun `si la red regresa durante la espera el reintento es inmediato`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()

        coordinator.start()
        runCurrent()
        assertEquals(1, repository.signInCalls)

        repository.nextSignInResult = SignInResult.Success
        connectivity.online.value = false
        runCurrent()
        connectivity.online.value = true
        runCurrent()
        assertEquals(SilentSessionState.SignedIn, coordinator.state.value)
        assertEquals(2, repository.signInCalls)
    }

    @Test
    fun `la limpieza de sesion cancela el trabajo y regresa a Idle`() = runTest {
        // RNF-03/RF-16: la salida del espacio interno corta la confirmación en
        // curso; el blob NO se toca (sobrevivir es el punto de la opción C).
        credentials.save("usuaria@correo.co", "clave-segura-1")
        connectivity.online.value = false
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()
        coordinator.start()
        runCurrent()

        coordinator.onSessionCleanup()
        runCurrent()

        assertEquals(SilentSessionState.Idle, coordinator.state.value)
        assertNotNull(credentials.stored)

        // La red regresa DESPUÉS de la salida: nada debe reintentar.
        repository.nextSignInResult = SignInResult.Success
        connectivity.online.value = true
        runCurrent()
        assertEquals(SilentSessionState.Idle, coordinator.state.value)
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `start es idempotente mientras la confirmacion sigue viva`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        connectivity.online.value = false
        repository.nextSignInResult = SignInResult.GenericFailure
        val coordinator = createCoordinator()

        coordinator.start()
        runCurrent()
        coordinator.start()
        runCurrent()

        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `tras la limpieza un nuevo start vuelve a confirmar`() = runTest {
        // Reingreso siguiente en el mismo proceso: sesión nueva, intento nuevo.
        credentials.save("usuaria@correo.co", "clave-segura-1")
        val coordinator = createCoordinator()
        coordinator.start()
        runCurrent()
        coordinator.onSessionCleanup()

        coordinator.start()
        runCurrent()

        assertEquals(SilentSessionState.SignedIn, coordinator.state.value)
        assertEquals(2, repository.signInCalls)
    }
}

/** Señal de conectividad simulada, movible a mano. */
private class FakeConnectivityMonitor : ConnectivityMonitor {
    val online = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = online
}
