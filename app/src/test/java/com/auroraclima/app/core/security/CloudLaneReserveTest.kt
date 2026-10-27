package com.auroraclima.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La franja de la nube en Inicio (v0.3.3): el margen que hace falta para que lo que se
 * desplaza termine antes de la nube, y nada más.
 */
class CloudLaneReserveTest {
    @Test
    fun `si el elemento pasa por debajo de la nube el margen lo deja justo encima`() {
        // Termina en 1700; la nube empieza en 1509 y el respiro es de 31.
        assertEquals(222f, cloudLaneHeight(elementBottom = 1_700f, cloudTop = 1_509f, gap = 31f), 0f)
    }

    @Test
    fun `si ya termina antes de la nube no agrega nada`() {
        assertEquals(0f, cloudLaneHeight(elementBottom = 1_400f, cloudTop = 1_509f, gap = 31f), 0f)
    }

    @Test
    fun `justo en el borde del respiro no agrega nada`() {
        assertEquals(0f, cloudLaneHeight(elementBottom = 1_478f, cloudTop = 1_509f, gap = 31f), 0f)
    }

    @Test
    fun `sin medida todavia no agrega nada`() {
        assertEquals(0f, cloudLaneHeight(elementBottom = null, cloudTop = 1_509f, gap = 31f), 0f)
    }
}
