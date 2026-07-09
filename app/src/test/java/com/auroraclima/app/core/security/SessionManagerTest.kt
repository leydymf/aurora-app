package com.auroraclima.app.core.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la sesión del espacio interno (RF-16, RNF-03) con tiempo virtual:
 * timeout de inactividad, gracia de background, limpieza y doble invocación segura.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private class CountingCleaner : SensitiveStateCleaner {
        var invocations = 0

        override fun onSessionCleanup() {
            invocations++
        }
    }

    /** Reloj que avanza a mano: simula la suspensión profunda (los timers no corren). */
    private class FakeMonotonicClock : MonotonicClock {
        var nowMs = 0L

        override fun nowMs(): Long = nowMs
    }

    private fun TestScope.sessionManager(
        vararg cleaners: SensitiveStateCleaner,
        clock: MonotonicClock = MonotonicClock { testScheduler.currentTime }
    ) = SessionManager(backgroundScope, clock, cleaners.toSet())

    private fun TestScope.collectCloseEvents(manager: SessionManager): List<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.closeEvents.collect { events += it }
        }
        return events
    }

    @Test
    fun `startSession activa la sesion y comienza inactiva`() = runTest {
        val manager = sessionManager()
        assertFalse(manager.isSessionActive.value)
        manager.startSession()
        assertTrue(manager.isSessionActive.value)
    }

    @Test
    fun `la sesion se cierra sola tras cinco minutos sin interaccion`() = runTest {
        val cleaner = CountingCleaner()
        val manager = sessionManager(cleaner)
        val events = collectCloseEvents(manager)
        manager.startSession()

        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS + 1)

        assertFalse(manager.isSessionActive.value)
        assertEquals(1, cleaner.invocations)
        assertEquals(1, events.size)
    }

    @Test
    fun `cada interaccion reinicia el temporizador de inactividad`() = runTest {
        val manager = sessionManager()
        manager.startSession()

        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS - 1)
        manager.registerInteraction()
        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS - 1)

        assertTrue(manager.isSessionActive.value)

        advanceTimeBy(2)
        assertFalse(manager.isSessionActive.value)
    }

    @Test
    fun `pasar a segundo plano cierra la sesion tras el plazo de gracia`() = runTest {
        val cleaner = CountingCleaner()
        val manager = sessionManager(cleaner)
        manager.startSession()

        manager.appMovedToBackground()
        advanceTimeBy(SessionManager.BACKGROUND_GRACE_MS + 1)

        assertFalse(manager.isSessionActive.value)
        assertEquals(1, cleaner.invocations)
    }

    @Test
    fun `volver a primer plano antes del plazo conserva la sesion`() = runTest {
        val manager = sessionManager()
        manager.startSession()

        manager.appMovedToBackground()
        advanceTimeBy(SessionManager.BACKGROUND_GRACE_MS - 1)
        manager.appReturnedToForeground()
        advanceTimeBy(SessionManager.BACKGROUND_GRACE_MS)

        assertTrue(manager.isSessionActive.value)
    }

    @Test
    fun `una suspension mas larga que la gracia cierra al volver aunque el timer no corriera`() = runTest {
        val clock = FakeMonotonicClock()
        val manager = sessionManager(clock = clock)
        manager.startSession()

        manager.appMovedToBackground()
        // Suspensión profunda: el reloj monótono avanza, los timers no (auditoría H-3).
        clock.nowMs += SessionManager.BACKGROUND_GRACE_MS + 1
        manager.appReturnedToForeground()

        assertFalse(manager.isSessionActive.value)
    }

    @Test
    fun `quickExit cierra de inmediato y descarta los temporizadores`() = runTest {
        val cleaner = CountingCleaner()
        val manager = sessionManager(cleaner)
        val events = collectCloseEvents(manager)
        manager.startSession()

        manager.quickExit()

        assertFalse(manager.isSessionActive.value)
        assertEquals(1, cleaner.invocations)
        assertEquals(1, events.size)

        // Los temporizadores quedaron cancelados: el tiempo no produce más cierres.
        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS * 2)
        assertEquals(1, events.size)
    }

    @Test
    fun `la doble invocacion del cierre limpia y notifica una sola vez`() = runTest {
        val cleaner = CountingCleaner()
        val manager = sessionManager(cleaner)
        val events = collectCloseEvents(manager)
        manager.startSession()

        manager.closeSession()
        manager.closeSession()
        manager.quickExit()

        assertEquals(1, cleaner.invocations)
        assertEquals(1, events.size)
    }

    @Test
    fun `un cleaner que falla no bloquea a los demas`() = runTest {
        val failing = SensitiveStateCleaner { error("fallo simulado") }
        val counting = CountingCleaner()
        val manager = sessionManager(failing, counting)
        manager.startSession()

        manager.closeSession()

        assertFalse(manager.isSessionActive.value)
        assertEquals(1, counting.invocations)
    }

    @Test
    fun `los cleaners dinamicos participan y pueden darse de baja`() = runTest {
        val manager = sessionManager()
        val cleaner = CountingCleaner()
        val registration = manager.registerCleaner(cleaner)

        manager.startSession()
        manager.closeSession()
        assertEquals(1, cleaner.invocations)

        registration.close()
        manager.startSession()
        manager.closeSession()
        assertEquals(1, cleaner.invocations)
    }

    @Test
    fun `sin sesion activa ni la interaccion ni el background hacen nada`() = runTest {
        val cleaner = CountingCleaner()
        val manager = sessionManager(cleaner)
        val events = collectCloseEvents(manager)

        manager.registerInteraction()
        manager.appMovedToBackground()
        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS * 2)

        assertEquals(0, cleaner.invocations)
        assertEquals(0, events.size)
    }

    @Test
    fun `una nueva sesion tras el cierre vuelve a temporizar desde cero`() = runTest {
        val manager = sessionManager()
        val events = collectCloseEvents(manager)

        manager.startSession()
        manager.closeSession()
        manager.startSession()
        assertTrue(manager.isSessionActive.value)

        advanceTimeBy(SessionManager.INACTIVITY_TIMEOUT_MS + 1)
        assertFalse(manager.isSessionActive.value)
        assertEquals(2, events.size)
    }
}
