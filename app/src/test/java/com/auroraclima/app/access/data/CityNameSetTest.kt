package com.auroraclima.app.access.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityNameSetTest {
    private val cities = CityNameSet(
        sequenceOf("Bogotá", "Güepsa", "El Peñón", "San Vicente de Chucurí", "", "  ")
    )

    @Test
    fun `compara sin tildes ni mayusculas`() {
        // RF-08 criterio 2: «Bogotá», «bogota» y «BOGOTA» son la misma ciudad
        assertTrue("bogota" in cities)
        assertTrue("BOGOTÁ" in cities)
        assertTrue("Bogota" in cities)
        assertTrue("guepsa" in cities)
        assertTrue("el penon" in cities)
    }

    @Test
    fun `ignora espacios alrededor y colapsa los internos`() {
        assertTrue("  Bogotá " in cities)
        assertTrue("san  vicente   de chucurí" in cities)
    }

    @Test
    fun `un codigo que no es ciudad no coincide`() {
        assertFalse("cielo2026" in cities)
        assertFalse("bogota123" in cities)
        assertFalse("" in cities)
    }

    @Test
    fun `las lineas vacias del recurso se descartan`() {
        assertEquals(4, cities.size)
    }

    @Test
    fun `la normalizacion cubre enie y dieresis`() {
        assertEquals("el penon", CityNameSet.normalize("El Peñón"))
        assertEquals("guepsa", CityNameSet.normalize("Güepsa"))
        assertEquals("sao paulo", CityNameSet.normalize("São Paulo"))
    }
}
