package com.auroraclima.app.questionnaire.data.remote

import com.auroraclima.app.questionnaire.data.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El parsing del formulario usa casts seguros: un campo con tipo inesperado
 * equivale a un campo ausente y nunca lanza (RF-25). El mapper todo-o-nada
 * decide después si el documento completo se descarta.
 */
class QuestionnaireDocumentParsingTest {
    @Test
    fun `un documento bien formado produce el DTO completo`() {
        val dto = wellFormedForm().toFormDto("f01")

        assertEquals("f01", dto.id)
        assertEquals(1L, dto.schemaVersion)
        assertEquals(2L, dto.version)
        assertEquals("Título", dto.title)
        assertEquals(ScaleOptionDto("s0", 1L, "Nunca", 0L), dto.scale.single())
        assertEquals(QuestionDto("q01", 1L, "Pregunta", 2L, "n01"), dto.questions.single())
        assertEquals(
            LevelDto("n01", 1L, 0L, "Nivel", "Resumen", listOf("Consejo"), listOf("r01")),
            dto.levels.single()
        )
    }

    @Test
    fun `campos de nivel superior con tipo incorrecto quedan nulos sin lanzar`() {
        val dto = mapOf(
            "schemaVersion" to "uno",
            "version" to "dos",
            "title" to 123L,
            "intro" to listOf("no es texto"),
            "notice" to 456L
        ).toFormDto("f01")

        assertNull(dto.schemaVersion)
        assertNull(dto.version)
        assertNull(dto.title)
        assertNull(dto.intro)
        assertNull(dto.notice)
    }

    @Test
    fun `una lista embebida corrupta queda vacia sin lanzar`() {
        val dto = mapOf(
            "scale" to "no es lista",
            "questions" to mapOf("no" to "lista"),
            "levels" to 7L
        ).toFormDto("f01")

        assertTrue(dto.scale.isEmpty())
        assertTrue(dto.questions.isEmpty())
        assertTrue(dto.levels.isEmpty())
    }

    @Test
    fun `una entrada embebida que no es mapa produce una pieza vacia`() {
        val dto = mapOf(
            "questions" to listOf("texto suelto", 42L)
        ).toFormDto("f01")

        assertEquals(2, dto.questions.size)
        assertTrue(dto.questions.all { it.id == null && it.text == null })
    }

    @Test
    fun `una pieza embebida con tipos corruptos queda con campos nulos`() {
        val dto = mapOf(
            "levels" to listOf(
                mapOf("id" to 1L, "order" to "primero", "minScore" to "cero", "title" to true, "summary" to 9L)
            )
        ).toFormDto("f01")

        val level = dto.levels.single()
        assertNull(level.id)
        assertNull(level.order)
        assertNull(level.minScore)
        assertNull(level.title)
        assertNull(level.summary)
    }

    @Test
    fun `un peso decimal se rechaza en vez de truncarse`() {
        // Truncar 2,5 → 2 sesgaría el puntaje frente a umbrales calibrados;
        // el campo queda nulo y el mapper descarta el formulario completo.
        val dto = mapOf(
            "questions" to listOf(mapOf("id" to "q01", "order" to 1L, "text" to "Pregunta", "weight" to 2.5))
        ).toFormDto("f01")

        assertNull(dto.questions.single().weight)
    }

    @Test
    fun `un mapa vacio produce un DTO sin datos`() {
        val dto = emptyMap<String, Any?>().toFormDto("f01")

        assertNull(dto.schemaVersion)
        assertNull(dto.version)
        assertTrue(dto.scale.isEmpty() && dto.questions.isEmpty() && dto.levels.isEmpty())
    }

    @Test
    fun `un formulario malformado termina descartado por el mapper`() {
        // Cadena completa RF-25: tipos corruptos → DTO con nulos → dominio nulo.
        val corrupt = wellFormedForm().plus("notice" to 123L).toFormDto("f01")

        assertNull(corrupt.toDomain())
    }

    private fun wellFormedForm(): Map<String, Any?> = mapOf(
        "schemaVersion" to 1L,
        "version" to 2L,
        "title" to "Título",
        "intro" to "Intro",
        "notice" to "Aviso",
        "scale" to listOf(mapOf("id" to "s0", "order" to 1L, "label" to "Nunca", "value" to 0L)),
        "questions" to listOf(
            mapOf("id" to "q01", "order" to 1L, "text" to "Pregunta", "weight" to 2L, "alertLevelId" to "n01")
        ),
        "levels" to listOf(
            mapOf(
                "id" to "n01",
                "order" to 1L,
                "minScore" to 0L,
                "title" to "Nivel",
                "summary" to "Resumen",
                "advice" to listOf("Consejo"),
                "routeIds" to listOf("r01")
            )
        )
    )
}
