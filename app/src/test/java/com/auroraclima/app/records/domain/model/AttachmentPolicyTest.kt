package com.auroraclima.app.records.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Política del almacén de evidencias (RF-33): lista blanca de MIME, tope de
 * tamaño, cupo por registro y mapeo de familias. Los límites son parte del
 * criterio de salida de la sesión, así que se fijan por prueba.
 */
class AttachmentPolicyTest {
    @Test
    fun `la lista blanca acepta pdf y audios comunes`() {
        assertTrue(AttachmentPolicy.isAllowedDocumentType("application/pdf"))
        assertTrue(AttachmentPolicy.isAllowedDocumentType("audio/mpeg"))
        assertTrue(AttachmentPolicy.isAllowedDocumentType("audio/mp4"))
        assertTrue(AttachmentPolicy.isAllowedDocumentType("audio/ogg"))
        assertTrue(AttachmentPolicy.isAllowedDocumentType("audio/amr"))
    }

    @Test
    fun `la lista blanca rechaza todo lo demas`() {
        // Video (peso), ejecutables, ofimática, imágenes (van por su propia vía
        // con re-codificación) y un MIME desconocido o ausente.
        assertFalse(AttachmentPolicy.isAllowedDocumentType("video/mp4"))
        assertFalse(AttachmentPolicy.isAllowedDocumentType("application/vnd.android.package-archive"))
        assertFalse(AttachmentPolicy.isAllowedDocumentType("application/msword"))
        assertFalse(AttachmentPolicy.isAllowedDocumentType("image/jpeg"))
        assertFalse(AttachmentPolicy.isAllowedDocumentType("text/html"))
        assertFalse(AttachmentPolicy.isAllowedDocumentType(null))
    }

    @Test
    fun `el tope de tamano acepta hasta el maximo y rechaza el exceso y el vacio`() {
        assertTrue(AttachmentPolicy.isWithinDocumentSize(1))
        assertTrue(AttachmentPolicy.isWithinDocumentSize(AttachmentPolicy.MAX_DOCUMENT_BYTES))
        assertFalse(AttachmentPolicy.isWithinDocumentSize(AttachmentPolicy.MAX_DOCUMENT_BYTES + 1))
        assertFalse(AttachmentPolicy.isWithinDocumentSize(0))
        assertFalse(AttachmentPolicy.isWithinDocumentSize(-1))
    }

    @Test
    fun `el cupo por registro es el maximo de la politica`() {
        assertTrue(AttachmentPolicy.hasCapacity(0))
        assertTrue(AttachmentPolicy.hasCapacity(AttachmentPolicy.MAX_PER_ENTRY - 1))
        assertFalse(AttachmentPolicy.hasCapacity(AttachmentPolicy.MAX_PER_ENTRY))
    }

    @Test
    fun `las familias se derivan del MIME y lo desconocido se descarta`() {
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.fromMime("image/jpeg"))
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.fromMime("image/webp"))
        assertEquals(AttachmentKind.DOCUMENT, AttachmentKind.fromMime("application/pdf"))
        assertEquals(AttachmentKind.AUDIO, AttachmentKind.fromMime("audio/ogg"))
        // Esquema futuro (§6): un MIME desconocido se omite sin romper nada.
        assertNull(AttachmentKind.fromMime("video/mp4"))
        assertNull(AttachmentKind.fromMime("application/octet-stream"))
    }

    @Test
    fun `toda entrada de la lista blanca tiene familia`() {
        // Garantiza que un tipo aceptado nunca cae al descarte del mapeo.
        AttachmentPolicy.allowedDocumentTypes.forEach { mime ->
            assertTrue("$mime sin familia", AttachmentKind.fromMime(mime) != null)
        }
    }
}
