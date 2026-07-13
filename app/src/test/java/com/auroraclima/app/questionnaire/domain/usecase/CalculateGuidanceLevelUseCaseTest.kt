package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motor del nivel orientativo (RF-29): umbrales inclusivos, casos borde
 * (todo sin responder, empates de nivel, puntaje bajo todos los umbrales)
 * y pisos de preguntas críticas. Las respuestas son un parámetro en
 * memoria: nada se persiste aquí ni existe API para hacerlo.
 */
class CalculateGuidanceLevelUseCaseTest {
    private val useCase = CalculateGuidanceLevelUseCase()

    // Escala F6: Nunca (0) / Alguna vez (1) / Con frecuencia (2).
    private val scale = listOf(
        ScaleOption("s0", 1, "Nunca", 0),
        ScaleOption("s1", 2, "Alguna vez", 1),
        ScaleOption("s2", 3, "Con frecuencia", 2)
    )

    // Umbrales del borrador semilla: 0 / 4 / 10 / 17.
    private val levels = listOf(
        level("n01", order = 1, minScore = 0),
        level("n02", order = 2, minScore = 4),
        level("n03", order = 3, minScore = 10),
        level("n04", order = 4, minScore = 17)
    )

    // Réplica del instrumento semilla: pesos 1,1,3,3,3,2 y pisos en q03..q06.
    private val questions = listOf(
        Question("q01", 1, "control", weight = 1),
        Question("q02", 2, "aislamiento", weight = 1),
        Question("q03", 3, "daño físico", weight = 3, alertLevelId = "n03"),
        Question("q04", 4, "amenazas", weight = 3, alertLevelId = "n04"),
        Question("q05", 5, "presión sexual", weight = 3, alertLevelId = "n03"),
        Question("q06", 6, "miedo", weight = 2, alertLevelId = "n03")
    )

    private val questionnaire = questionnaire()

    @Test
    fun `todo sin responder cae al nivel mas bajo con conteo en cero`() {
        // Caso borde: la usuaria puede avanzar sin responder (RF-27).
        val result = useCase(questionnaire, emptyMap())

        assertEquals("n01", result.level.id)
        assertEquals(0, result.score)
        assertEquals(0, result.answeredCount)
        assertEquals(6, result.questionCount)
    }

    @Test
    fun `el puntaje multiplica peso por valor de la opcion`() {
        // q03 pesa 3; "Alguna vez" vale 1 → 3 puntos, sin llegar al piso.
        val result = useCase(questionnaire, mapOf("q03" to "s1"))

        assertEquals(3, result.score)
        assertEquals(1, result.answeredCount)
        assertEquals("n01", result.level.id)
    }

    @Test
    fun `el umbral exacto pertenece a la banda superior`() {
        // 4 puntos exactos (q01 y q02 "Con frecuencia") → Moderado, no Variable.
        val result = useCase(questionnaire, mapOf("q01" to "s2", "q02" to "s2"))

        assertEquals(4, result.score)
        assertEquals("n02", result.level.id)
    }

    @Test
    fun `un punto bajo el umbral sigue en la banda inferior`() {
        // 3 puntos (q01 "Con frecuencia" + q02 "Alguna vez") → Variable.
        val result = useCase(questionnaire, mapOf("q01" to "s2", "q02" to "s1"))

        assertEquals(3, result.score)
        assertEquals("n01", result.level.id)
    }

    @Test
    fun `todas las respuestas intermedias suman a la banda que corresponde`() {
        // Todo "Alguna vez": 1+1+3+3+3+2 = 13 → Grave (≥10), sin pisos (valor no máximo).
        val answers = questions.associate { it.id to "s1" }

        val result = useCase(questionnaire, answers)

        assertEquals(13, result.score)
        assertEquals("n03", result.level.id)
        assertEquals(6, result.answeredCount)
    }

    @Test
    fun `si ningun umbral alcanza el puntaje cae al nivel menos severo`() {
        // Configuración sin nivel de arranque (minScore > 0 en todos).
        val startsHigh = questionnaire(
            levels = listOf(level("n02", order = 2, minScore = 4), level("n03", order = 3, minScore = 10))
        )

        val result = useCase(startsHigh, mapOf("q01" to "s2"))

        assertEquals(2, result.score)
        assertEquals("n02", result.level.id)
    }

    @Test
    fun `un empate de minScore lo gana el nivel mas severo`() {
        // Configuración ambigua publicada por error: sobre-orientar, nunca al revés.
        val tied = questionnaire(
            levels = listOf(
                level("n01", order = 1, minScore = 0),
                level("nA", order = 2, minScore = 5),
                level("nB", order = 3, minScore = 5)
            )
        )

        val result = useCase(tied, mapOf("q03" to "s2"))

        assertEquals(6, result.score)
        assertEquals("nB", result.level.id)
    }

    @Test
    fun `la pregunta critica con la opcion maxima impone su piso`() {
        // Solo amenazas "Con frecuencia": 6 puntos darían Moderado; el piso la
        // lleva a Extremo (así tratan los instrumentos los ítems críticos).
        val result = useCase(questionnaire, mapOf("q04" to "s2"))

        assertEquals(6, result.score)
        assertEquals("n04", result.level.id)
    }

    @Test
    fun `la pregunta critica sin la opcion maxima no activa el piso`() {
        // Amenazas "Alguna vez": 3 puntos → Variable; el piso exige el valor máximo.
        val result = useCase(questionnaire, mapOf("q04" to "s1"))

        assertEquals("n01", result.level.id)
    }

    @Test
    fun `el piso nunca baja un nivel base superior`() {
        // Puntaje total 24 → Extremo por umbral; el piso Grave de q03/q05/q06 no lo rebaja.
        val answers = questions.associate { it.id to "s2" }

        val result = useCase(questionnaire, answers)

        assertEquals(26, result.score)
        assertEquals("n04", result.level.id)
    }

    @Test
    fun `entre varios pisos activos rige el mas severo`() {
        // q03 (piso Grave) y q04 (piso Extremo) en la opción máxima.
        val result = useCase(questionnaire, mapOf("q03" to "s2", "q04" to "s2"))

        assertEquals(12, result.score)
        assertEquals("n04", result.level.id)
    }

    @Test
    fun `una opcion desconocida cuenta como no respondida`() {
        // Estado corrupto o esquema editado a mitad de sesión: aporta 0 y no cuenta.
        val result = useCase(questionnaire, mapOf("q03" to "sX", "q01" to "s2"))

        assertEquals(2, result.score)
        assertEquals(1, result.answeredCount)
    }

    @Test
    fun `una respuesta a una pregunta desconocida se ignora`() {
        val result = useCase(questionnaire, mapOf("qX" to "s2"))

        assertEquals(0, result.score)
        assertEquals(0, result.answeredCount)
        assertEquals("n01", result.level.id)
    }

    @Test
    fun `un alertLevelId sin resolver no rompe el calculo`() {
        // El mapper ya lo anula, pero el motor tampoco debe romperse (§3).
        val dangling = questionnaire(
            questions = listOf(Question("q01", 1, "texto", weight = 1, alertLevelId = "no-existe"))
        )

        val result = useCase(dangling, mapOf("q01" to "s2"))

        assertEquals(2, result.score)
        assertEquals("n01", result.level.id)
    }

    private fun questionnaire(questions: List<Question> = this.questions, levels: List<GuidanceLevel> = this.levels) =
        Questionnaire(
            id = "f01",
            version = 1,
            title = "Título",
            intro = "Intro",
            notice = "Aviso",
            scale = scale,
            questions = questions,
            levels = levels
        )

    private fun level(id: String, order: Int, minScore: Int) = GuidanceLevel(
        id = id,
        order = order,
        minScore = minScore,
        title = "Nivel $id",
        summary = "Resumen",
        advice = listOf("Consejo"),
        routeIds = listOf("r0$order")
    )
}
