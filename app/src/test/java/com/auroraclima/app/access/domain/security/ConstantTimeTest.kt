package com.auroraclima.app.access.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantTimeTest {
    @Test
    fun `arreglos iguales son iguales`() {
        val bytes = ByteArray(32) { it.toByte() }

        assertTrue(constantTimeEquals(bytes, bytes.copyOf()))
    }

    @Test
    fun `arreglos vacios son iguales`() {
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun `longitudes distintas nunca son iguales`() {
        assertFalse(constantTimeEquals(ByteArray(32), ByteArray(31)))
    }

    @Test
    fun `una diferencia en cualquier posicion se detecta`() {
        // Cubre en particular la primera y la última posición: no hay cortocircuito
        // que dependa de dónde esté la diferencia (RF-13)
        val base = ByteArray(32) { it.toByte() }
        for (position in base.indices) {
            val altered = base.copyOf().also { it[position] = (it[position] + 1).toByte() }

            assertFalse("diferencia en $position no detectada", constantTimeEquals(base, altered))
        }
    }
}
