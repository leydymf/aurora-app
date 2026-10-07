package com.auroraclima.app.core.startup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contención de fallos en el arranque (auditoría A-2 del cierre pre-merge):
 * ninguna tarea puede tumbar el proceso, y un fallo al construir el conjunto o
 * al correr una tarea no debe impedir las demás. El proceso muerto sería una
 * anomalía observable en una app de clima (RNF-05) y dejaría sin correr la
 * recuperación de borrados pendientes de RF-20.
 */
class AppStartupTest {
    private class CountingTask(private val failure: Throwable? = null) : AppInitializer {
        var calls = 0
            private set

        override suspend fun initialize() {
            calls++
            failure?.let { throw it }
        }
    }

    @Test
    fun `corre todas las tareas de arranque`() = runTest {
        val first = CountingTask()
        val second = CountingTask()

        runStartupTasks { linkedSetOf(first, second) }

        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `una tarea que lanza no impide las siguientes ni propaga`() = runTest {
        val failing = CountingTask(failure = IllegalStateException())
        val healthy = CountingTask()

        runStartupTasks { linkedSetOf(failing, healthy) }

        assertEquals(1, failing.calls)
        assertEquals(1, healthy.calls)
    }

    @Test
    fun `si el conjunto no se puede construir el arranque sigue sin propagar`() = runTest {
        var attempted = false

        runStartupTasks {
            attempted = true
            throw IllegalStateException("servicios ausentes")
        }

        // No propaga: la ausencia de un servicio del dispositivo no mata el proceso.
        assertTrue(attempted)
    }
}
