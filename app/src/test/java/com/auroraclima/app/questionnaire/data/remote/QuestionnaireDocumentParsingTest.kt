package com.auroraclima.app.questionnaire.data.remote

import com.auroraclima.app.questionnaire.data.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(2L, dto.schemaVersion)
        assertEquals(2L, dto.version)
        assertEquals("Título", dto.title)
        assertEquals(ScaleOptionDto("s0", 1L, "Nunca", 0L), dto.scales.single().options.single())
        assertEquals("freq", dto.scales.single().id)
        assertEquals(BlockDto("a", 1L, "Bloque"), dto.blocks.single())
        assertEquals(QuestionDto("q01", 1L, "a", "freq", "Pregunta", 2L, false), dto.questions.single())
        assertEquals(DimensionDto("d1", 1L, "psicológica", listOf("q01"), 1L), dto.dimensions.single())
        assertEquals(RecognitionDto("Prefijo", "Vacío"), dto.recognition)
        assertEquals(ThresholdFlagDto("q01", 1L, "n01"), dto.thresholdFlags.single())
        assertEquals(CountFlagDto(listOf("q01"), 1L, 2L, "n01"), dto.countFlags.single())
        assertEquals(MunicipalityDto("m01", 1L, "Bucaramanga"), dto.municipalities.single())
        assertEquals(
            LevelDto(
                id = "n01",
                order = 1L,
                minScore = 0L,
                title = "Nivel",
                summary = "Resumen",
                advice = listOf("Consejo"),
                lineIds = listOf("l01"),
                agencyIds = listOf("a13"),
                municipalAgencyIds = mapOf("bucaramanga" to listOf("a07"))
            ),
            dto.levels.single()
        )
    }

    @Test
    fun `campos de nivel superior con tipo incorrecto quedan nulos sin lanzar`() {
        val dto = mapOf(
            "schemaVersion" to "dos",
            "version" to "dos",
            "title" to 123L,
            "intro" to listOf("no es texto"),
            "notice" to 456L,
            "recognition" to "no es mapa"
        ).toFormDto("f01")

        assertNull(dto.schemaVersion)
        assertNull(dto.version)
        assertNull(dto.title)
        assertNull(dto.intro)
        assertNull(dto.notice)
        assertNull(dto.recognition)
    }

    @Test
    fun `una lista embebida corrupta queda vacia sin lanzar`() {
        val dto = mapOf(
            "scales" to "no es lista",
            "questions" to mapOf("no" to "lista"),
            "levels" to 7L,
            "dimensions" to 3L,
            "municipalities" to false
        ).toFormDto("f01")

        assertTrue(dto.scales.isEmpty())
        assertTrue(dto.questions.isEmpty())
        assertTrue(dto.levels.isEmpty())
        assertTrue(dto.dimensions.isEmpty())
        assertTrue(dto.municipalities.isEmpty())
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
    fun `el campo context ausente o corrupto se lee como falso`() {
        val ausente = mapOf("questions" to listOf(mapOf("id" to "q01"))).toFormDto("f01")
        assertFalse(ausente.questions.single().context)

        val presente = mapOf(
            "questions" to listOf(mapOf("id" to "p0", "context" to true))
        ).toFormDto("f01")
        assertTrue(presente.questions.single().context)
    }

    @Test
    fun `el mapa de accesos por municipio tolera claves y listas corruptas`() {
        val dto = mapOf(
            "levels" to listOf(
                mapOf(
                    "id" to "n01",
                    "municipalAgencyIds" to mapOf(
                        "bucaramanga" to listOf("a07", 5L),
                        "giron" to "no es lista",
                        7L to listOf("a11")
                    )
                )
            )
        ).toFormDto("f01")

        val municipal = dto.levels.single().municipalAgencyIds
        assertEquals(listOf("a07"), municipal["bucaramanga"])
        assertEquals(emptyList<String>(), municipal["giron"])
        // La clave que no es texto se descarta.
        assertFalse(municipal.containsKey("7"))
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
        assertNull(dto.recognition)
        assertTrue(dto.scales.isEmpty() && dto.questions.isEmpty() && dto.levels.isEmpty())
    }

    @Test
    fun `un formulario malformado termina descartado por el mapper`() {
        // Cadena completa RF-25: tipos corruptos → DTO con nulos → dominio nulo.
        val corrupt = wellFormedForm().plus("notice" to 123L).toFormDto("f01")

        assertNull(corrupt.toDomain())
    }

    @Test
    fun `un formulario bien formado se mapea a dominio`() {
        assertEquals("f01", wellFormedForm().toFormDto("f01").toDomain()?.id)
    }

    private fun wellFormedForm(): Map<String, Any?> = mapOf(
        "schemaVersion" to 2L,
        "version" to 2L,
        "title" to "Título",
        "intro" to "Intro",
        "notice" to "Aviso",
        "scales" to listOf(
            mapOf(
                "id" to "freq",
                "options" to listOf(mapOf("id" to "s0", "order" to 1L, "label" to "Nunca", "value" to 0L))
            )
        ),
        "blocks" to listOf(mapOf("id" to "a", "order" to 1L, "title" to "Bloque")),
        "questions" to listOf(
            mapOf(
                "id" to "q01",
                "order" to 1L,
                "blockId" to "a",
                "scaleId" to "freq",
                "text" to "Pregunta",
                "weight" to 2L,
                "context" to false
            )
        ),
        "dimensions" to listOf(
            mapOf(
                "id" to "d1",
                "order" to 1L,
                "label" to "psicológica",
                "questionIds" to listOf("q01"),
                "minValue" to 1L
            )
        ),
        "recognition" to mapOf("prefix" to "Prefijo", "empty" to "Vacío"),
        "thresholdFlags" to listOf(mapOf("questionId" to "q01", "minValue" to 1L, "levelId" to "n01")),
        "countFlags" to listOf(
            mapOf("questionIds" to listOf("q01"), "eachMinValue" to 1L, "countAtLeast" to 2L, "levelId" to "n01")
        ),
        "municipalities" to listOf(mapOf("id" to "m01", "order" to 1L, "label" to "Bucaramanga")),
        "levels" to listOf(
            mapOf(
                "id" to "n01",
                "order" to 1L,
                "minScore" to 0L,
                "title" to "Nivel",
                "summary" to "Resumen",
                "advice" to listOf("Consejo"),
                "lineIds" to listOf("l01"),
                "agencyIds" to listOf("a13"),
                "municipalAgencyIds" to mapOf("bucaramanga" to listOf("a07"))
            )
        )
    )
}
