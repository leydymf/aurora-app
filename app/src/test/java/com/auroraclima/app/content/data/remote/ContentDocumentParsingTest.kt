package com.auroraclima.app.content.data.remote

import com.auroraclima.app.content.data.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El parsing de documentos crudos usa casts seguros: un campo con tipo
 * inesperado equivale a un campo ausente y nunca lanza (RF-25). El mapper
 * de dominio decide después si el documento se omite.
 */
class ContentDocumentParsingTest {
    @Test
    fun `un documento bien formado produce el DTO completo`() {
        val dto = mapOf(
            "schemaVersion" to 1L,
            "order" to 2L,
            "title" to "Título",
            "summary" to "Resumen",
            "signals" to listOf("señal"),
            "examples" to listOf("ejemplo")
        ).toTopicDto("t01")

        assertEquals(
            TopicDto("t01", 1L, 2L, "Título", "Resumen", listOf("señal"), listOf("ejemplo")),
            dto
        )
    }

    @Test
    fun `cualquier representacion entera se acepta y un decimal se rechaza`() {
        // Firestore entrega Long, pero el cast pasa por Number para tolerar
        // cualquier representación entera; un decimal no entero se trata como
        // corrupto — truncarlo alteraría el orden sin descartar el documento.
        assertEquals(1L, mapOf("schemaVersion" to 1).toTopicDto("t01").schemaVersion)
        assertEquals(3L, mapOf("order" to 3.0).toTopicDto("t01").order)
        assertNull(mapOf("order" to 3.7).toTopicDto("t01").order)
    }

    @Test
    fun `campos con tipo incorrecto quedan nulos sin lanzar`() {
        val dto = mapOf(
            "schemaVersion" to "uno",
            "order" to "primero",
            "title" to 123L,
            "summary" to listOf("no es texto"),
            "signals" to "no es lista",
            "examples" to listOf(1L, 2L)
        ).toTopicDto("t01")

        assertNull(dto.schemaVersion)
        assertNull(dto.order)
        assertNull(dto.title)
        assertNull(dto.summary)
        assertTrue(dto.signals.isEmpty())
        assertTrue(dto.examples.isEmpty())
    }

    @Test
    fun `un mapa vacio produce un DTO sin datos`() {
        val dto = emptyMap<String, Any?>().toLineDto("l01")

        assertNull(dto.schemaVersion)
        assertNull(dto.order)
        assertNull(dto.name)
        assertNull(dto.number)
        assertNull(dto.description)
        assertNull(dto.hours)
    }

    @Test
    fun `una ruta con agencyIds corrupto conserva el resto de campos`() {
        val dto = mapOf(
            "schemaVersion" to 1L,
            "order" to 1L,
            "title" to "Salud",
            "summary" to "Resumen",
            "agencyIds" to mapOf("no" to "lista")
        ).toRouteDto("r01")

        assertEquals("Salud", dto.title)
        assertTrue(dto.agencyIds.isEmpty())
    }

    @Test
    fun `una entidad con telefonos mixtos conserva solo los textos`() {
        val dto = mapOf(
            "schemaVersion" to 1L,
            "name" to "Entidad",
            "role" to "Qué hace",
            "whenToUse" to "Cuándo acudir",
            "phones" to listOf("155", 123L)
        ).toAgencyDto("a01")

        assertEquals(listOf("155"), dto.phones)
    }

    @Test
    fun `un documento malformado termina omitido por el mapper`() {
        // Cadena completa RF-25: tipos corruptos → DTO con nulos → dominio nulo.
        val corrupt = mapOf(
            "schemaVersion" to 1L,
            "order" to 1L,
            "title" to 123L,
            "summary" to "Resumen"
        ).toTopicDto("t01")

        assertNull(corrupt.toDomain())
    }
}
