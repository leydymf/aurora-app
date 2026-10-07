package com.auroraclima.app.records.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolución del tipo de un registro contra el catálogo publicado (ADR
 * 2026-10-03): publicado → nombre y visual vivos; retirado → último nombre
 * conocido sin visual; sin tipo o sin instantánea → rótulo neutro. Nunca lanza.
 */
class ResolvedKindTest {
    private val catalog = listOf(
        EntryKind("t01", "Primera", "figure", "blue"),
        EntryKind("t05", "Quinta", "device", "lilac")
    )

    @Test
    fun `un tipo publicado toma nombre y visual del catalogo, no de la instantanea`() {
        val resolved = resolveKind("t01", "Nombre viejo", catalog)

        assertTrue(resolved.isPublished)
        assertEquals("Primera", resolved.label)
        assertEquals("figure", resolved.icon)
        assertEquals("blue", resolved.accent)
    }

    @Test
    fun `un tipo retirado conserva su ultimo nombre conocido y pierde el visual`() {
        val resolved = resolveKind("t09", "Nombre que tuvo", catalog)

        assertFalse(resolved.isPublished)
        assertEquals("Nombre que tuvo", resolved.label)
        assertNull(resolved.icon)
        assertNull(resolved.accent)
    }

    @Test
    fun `un tipo retirado sin instantanea queda sin nombre para el rotulo neutro`() {
        val resolved = resolveKind("t09", "  ", catalog)

        assertFalse(resolved.isPublished)
        assertNull(resolved.label)
    }

    @Test
    fun `el registro sin tipo se resuelve como no publicado y sin nombre`() {
        val resolved = resolveKind(UNSPECIFIED_KIND_ID, "", catalog)

        assertEquals(UNSPECIFIED_KIND_ID, resolved.id)
        assertFalse(resolved.isPublished)
        assertNull(resolved.label)
    }

    @Test
    fun `sin catalogo todos los tipos degradan a su instantanea`() {
        val resolved = resolveKind("t01", "Primera (guardada)", emptyList())

        assertFalse(resolved.isPublished)
        assertEquals("Primera (guardada)", resolved.label)
    }
}
