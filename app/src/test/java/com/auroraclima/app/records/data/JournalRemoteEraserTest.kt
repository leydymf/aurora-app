package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.remote.JournalPurgeRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El eraser remoto de RF-20 traduce la purga a Result (§3): un lanzamiento de la
 * fuente (sin red, sin sesión) llega aguas arriba como failure — que DETIENE la
 * eliminación — y nunca como excepción hacia la UI.
 */
class JournalRemoteEraserTest {
    @Test
    fun `la purga exitosa es un success`() = runTest {
        var calls = 0
        val eraser = JournalRemoteEraser { calls++ }

        assertEquals(Result.success(Unit), eraser.erase())
        assertEquals(1, calls)
    }

    @Test
    fun `el lanzamiento de la fuente se traduce a failure sin propagarse`() = runTest {
        val eraser = JournalRemoteEraser(
            JournalPurgeRemoteSource { throw IllegalStateException() }
        )

        assertTrue(eraser.erase().isFailure)
    }
}
