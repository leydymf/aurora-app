package com.auroraclima.app.core.security

import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red de seguridad de RF-20 (auditoría H-1/H-3): si el proceso murió entre
 * borrar la cuenta y terminar lo local, el arranque completa los erasers; y si
 * uno vuelve a fallar, la marca queda izada para el siguiente arranque.
 */
class PendingErasureRecoveryTest {
    private class FakeFlag(var raised: Boolean) : PendingErasureFlag {
        override suspend fun raise(): Boolean {
            raised = true
            return true
        }

        override suspend fun isRaised(): Boolean = raised

        override suspend fun clear() {
            raised = false
        }
    }

    private class CountingEraser(private val failure: Exception? = null) : LocalAccountDataEraser {
        var calls = 0
            private set

        override suspend fun erase() {
            calls++
            failure?.let { throw it }
        }
    }

    @Test
    fun `sin marca izada el arranque no borra nada`() = runTest {
        val eraser = CountingEraser()
        var resolved = false
        PendingErasureRecovery(
            FakeFlag(raised = false),
            Provider {
                resolved = true
                setOf(eraser)
            }
        ).initialize()

        assertEquals(0, eraser.calls)
        // Con la marca abajo el conjunto ni siquiera se construye (ADR 2026-10-05).
        assertFalse(resolved)
    }

    @Test
    fun `con marca izada completa los erasers y la retira`() = runTest {
        val flag = FakeFlag(raised = true)
        val first = CountingEraser()
        val second = CountingEraser()

        PendingErasureRecovery(flag, Provider { linkedSetOf(first, second) }).initialize()

        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
        assertFalse(flag.raised)
    }

    @Test
    fun `si el conjunto de erasers no se puede construir la marca sigue izada`() = runTest {
        val flag = FakeFlag(raised = true)

        // Servicios ausentes al construir el conjunto (auditoría A-2): no propaga
        // y el siguiente arranque lo reintenta, así que nada se da por borrado.
        PendingErasureRecovery(flag, Provider { throw IllegalStateException() }).initialize()

        assertTrue(flag.raised)
    }

    @Test
    fun `un eraser que vuelve a fallar deja la marca izada y no detiene al resto`() = runTest {
        val flag = FakeFlag(raised = true)
        val failing = CountingEraser(failure = IllegalStateException())
        val healthy = CountingEraser()

        PendingErasureRecovery(flag, Provider { linkedSetOf(failing, healthy) }).initialize()

        assertEquals(1, failing.calls)
        assertEquals(1, healthy.calls)
        assertTrue(flag.raised)
    }
}
