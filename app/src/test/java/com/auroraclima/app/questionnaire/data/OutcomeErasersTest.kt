package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.OutcomePurgeRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cascada RF-20 de los resultados guardados: el eraser remoto traduce la purga
 * a Result (§3) — un lanzamiento de la fuente (sin red, sin sesión) llega como
 * failure que DETIENE la eliminación, nunca como excepción hacia la UI.
 */
class OutcomeErasersTest {
    @Test
    fun `la purga exitosa es un success`() = runTest {
        var calls = 0
        val eraser = OutcomeRemoteEraser { calls++ }

        assertEquals(Result.success(Unit), eraser.erase())
        assertEquals(1, calls)
    }

    @Test
    fun `el lanzamiento de la fuente se traduce a failure sin propagarse`() = runTest {
        val eraser = OutcomeRemoteEraser(
            OutcomePurgeRemoteSource { throw IllegalStateException() }
        )

        assertTrue(eraser.erase().isFailure)
    }
}
