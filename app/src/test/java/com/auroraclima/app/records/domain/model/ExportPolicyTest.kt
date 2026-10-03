package com.auroraclima.app.records.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Política de la exportación (RF-45 criterio 4): nombre neutro y largo mínimo de contraseña. */
class ExportPolicyTest {
    @Test
    fun `el nombre del archivo es neutro y lleva solo la fecha`() {
        assertEquals("documento_2026-10-03.pdf", ExportPolicy.fileName(LocalDate.of(2026, 10, 3)))
    }

    @Test
    fun `el nombre no contiene terminos que delaten el contenido`() {
        val name = ExportPolicy.fileName(LocalDate.of(2026, 1, 9)).lowercase()
        listOf("registro", "hecho", "aurora", "export").forEach { term ->
            assertFalse("el nombre no debe contener «$term»", name.contains(term))
        }
    }

    @Test
    fun `la contrasena exige el largo minimo`() {
        assertFalse(ExportPolicy.isPasswordLongEnough(""))
        assertFalse(ExportPolicy.isPasswordLongEnough("123456789"))
        assertTrue(ExportPolicy.isPasswordLongEnough("1234567890"))
    }
}
