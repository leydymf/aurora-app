package com.auroraclima.app.accounts.data

import com.auroraclima.app.core.security.MonotonicClock
import com.auroraclima.app.core.security.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Cierre de sesión explícito (RF-19): el «Cerrar sesión» de configuración
 * converge en la rutina única de cierre (quickExit → closeSession). Aquí se
 * prueba la cadena completa con el cleaner real de accounts: la sesión del
 * proveedor se cierra —el siguiente acceso exige credenciales (criterio 1)— y
 * el estado interno queda limpio; el pop a la fachada (criterio 2) lo hace
 * SessionGuard al recibir el evento de cierre, igual que la salida rápida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignOutSessionCleanupTest {
    private class FakeAuthService : AuthService {
        var hasSession = true
            private set

        var signOutCalls = 0
            private set

        override suspend fun createUser(email: String, password: String) = Unit

        override suspend fun signIn(email: String, password: String) = Unit

        override fun hasActiveSession(): Boolean = hasSession

        override fun signOut() {
            signOutCalls++
            hasSession = false
        }

        override suspend fun reauthenticate(password: String) = Unit

        override suspend fun deleteCurrentUser() = Unit

        override suspend fun updatePassword(newPassword: String) = Unit
    }

    @Test
    fun `el cierre explicito limpia la sesion del proveedor y el estado interno`() = runTest {
        val service = FakeAuthService()
        val manager = SessionManager(
            backgroundScope,
            MonotonicClock { testScheduler.currentTime },
            setOf(AuthSessionCleaner(service))
        )
        val closeEvents = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.closeEvents.collect { closeEvents += it }
        }
        manager.startSession()

        manager.quickExit()

        // Criterio 1: sin sesión del proveedor, volver a entrar exige credenciales.
        assertEquals(1, service.signOutCalls)
        assertFalse(service.hasActiveSession())
        // Estado interno cerrado y evento emitido: SessionGuard hace el pop al
        // clima (criterio 2), la misma vía probada de la salida rápida (RF-16).
        assertFalse(manager.isSessionActive.value)
        assertEquals(1, closeEvents.size)
    }

    @Test
    fun `un segundo cierre no repite la limpieza`() = runTest {
        val service = FakeAuthService()
        val manager = SessionManager(
            backgroundScope,
            MonotonicClock { testScheduler.currentTime },
            setOf(AuthSessionCleaner(service))
        )
        manager.startSession()

        manager.quickExit()
        manager.quickExit()

        assertEquals(1, service.signOutCalls)
    }
}
