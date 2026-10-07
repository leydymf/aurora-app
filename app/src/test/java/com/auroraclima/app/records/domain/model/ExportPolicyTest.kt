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

    @Test
    fun `el archivo incrustado se numera por documento y toma la extension del MIME`() {
        assertEquals("audio_1.mp3", ExportPolicy.embeddedFileName("audio/mpeg", 1))
        assertEquals("audio_2.wav", ExportPolicy.embeddedFileName("audio/x-wav", 2))
        assertEquals("audio_3.m4a", ExportPolicy.embeddedFileName("audio/mp4", 3))
        assertEquals("audio_4.bin", ExportPolicy.embeddedFileName("audio/desconocido", 4))
    }

    @Test
    fun `los adjuntos caben en un solo archivo hasta el tope`() {
        assertTrue(ExportPolicy.fitsInOneFile(0))
        assertTrue(ExportPolicy.fitsInOneFile(ExportPolicy.MAX_TOTAL_ATTACHMENT_BYTES))
        assertFalse(ExportPolicy.fitsInOneFile(ExportPolicy.MAX_TOTAL_ATTACHMENT_BYTES + 1))
    }
}
