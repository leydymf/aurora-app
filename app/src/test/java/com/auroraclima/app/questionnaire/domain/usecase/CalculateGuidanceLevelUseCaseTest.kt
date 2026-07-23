package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.Block
import com.auroraclima.app.questionnaire.domain.model.CountFlag
import com.auroraclima.app.questionnaire.domain.model.Dimension
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Municipality
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.Recognition
import com.auroraclima.app.questionnaire.domain.model.Scale
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import com.auroraclima.app.questionnaire.domain.model.ThresholdFlag
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motor del instrumento validado (documento §5.4), en dos vías. Los casos se
 * derivan del documento: garantía de cobertura §5.5, cada bandera crítica, la
 * regla de 2+ banderas, el caso todo-Nunca → Variable, los bordes exactos de
 * umbral (9/10, 24/25, 40/41, 66) y tipos encendidos con riesgo bajo. Las
 * respuestas son un parámetro en memoria: nada se persiste (RF-27/28/31).
 */
class CalculateGuidanceLevelUseCaseTest {
    private val useCase = CalculateGuidanceLevelUseCase()

    // ---------------------------------------------------------------------
    // Réplica del instrumento validado (documento §4/§5): 12 preguntas (P0 de
    // contexto sin puntaje + 11 puntuables), pesos 1,1,2,1,2,3 | 3,3,2,2,2,
    // banderas de §5.3 y umbrales 0/10/25/41.
    // ---------------------------------------------------------------------
    private val freq = Scale(
        "freq",
        listOf(
            ScaleOption("f0", 1, "Nunca", 0),
            ScaleOption("f1", 2, "Una vez", 1),
            ScaleOption("f2", 3, "Varias veces", 2),
            ScaleOption("f3", 4, "Muchas veces o casi siempre", 3)
        )
    )
    private val fear = Scale(
        "fear",
        listOf(
            ScaleOption("r0", 1, "Nunca", 0),
            ScaleOption("r1", 2, "A veces", 1),
            ScaleOption("r2", 3, "Con frecuencia", 2),
            ScaleOption("r3", 4, "Todo el tiempo", 3)
        )
    )
    private val link = Scale(
        "link",
        listOf(
            ScaleOption("k0", 1, "Pareja o expareja", 0),
            ScaleOption("k1", 2, "Familiar", 0)
        )
    )

    private val instrument = Questionnaire(
        id = "f01",
        version = 2,
        title = "Título",
        intro = "Intro",
        notice = "Aviso",
        scales = listOf(freq, fear, link),
        blocks = listOf(
            Block("a", 1, "Lo que estás viviendo"),
            Block("b", 2, "Tu seguridad")
        ),
        questions = listOf(
            Question("p0", 0, "a", "link", "vínculo", weight = 0, context = true),
            Question("p1", 1, "a", "freq", "psicológica", weight = 1),
            Question("p2", 2, "a", "freq", "control", weight = 1),
            Question("p3", 3, "a", "freq", "aislamiento", weight = 2),
            Question("p4", 4, "a", "freq", "económica", weight = 1),
            Question("p5", 5, "a", "freq", "física", weight = 2),
            Question("p8", 6, "a", "freq", "sexual", weight = 3),
            Question("p6", 7, "b", "freq", "estrangulamiento", weight = 3),
            Question("p7", 8, "b", "freq", "armas", weight = 3),
            Question("p9", 9, "b", "freq", "escalamiento", weight = 2),
            Question("p10", 10, "b", "freq", "terceros", weight = 2),
            Question("p11", 11, "b", "fear", "miedo", weight = 2)
        ),
        dimensions = listOf(
            Dimension("psico", 1, "violencia psicológica", listOf("p1", "p2", "p3"), 1),
            Dimension("fisica", 2, "violencia física", listOf("p5", "p6"), 1),
            Dimension("sexual", 3, "violencia sexual", listOf("p8"), 1),
            Dimension("economica", 4, "violencia económica", listOf("p4"), 1)
        ),
        recognition = Recognition("Por tus respuestas, aparecen señales de", "No aparecen señales"),
        thresholdFlags = listOf(
            ThresholdFlag("p6", 1, "grave"),
            ThresholdFlag("p6", 2, "extremo"),
            ThresholdFlag("p7", 1, "grave"),
            ThresholdFlag("p7", 2, "extremo"),
            ThresholdFlag("p8", 1, "grave"),
            ThresholdFlag("p11", 3, "grave")
        ),
        countFlags = listOf(CountFlag(listOf("p6", "p7", "p8"), 1, 2, "extremo")),
        municipalities = listOf(Municipality("bucaramanga", 1, "Bucaramanga")),
        levels = listOf(
            level("variable", 1, 0),
            level("moderado", 2, 10),
            level("grave", 3, 25),
            level("extremo", 4, 41)
        )
    )

    // ---- §5.5 Garantía de cobertura y caso todo-Nunca ----

    @Test
    fun `todo sin responder cae a Variable con conteo en cero`() {
        val result = useCase(instrument, emptyMap())

        assertEquals("variable", result.level.id)
        assertEquals(0, result.score)
        assertEquals(0, result.answeredCount)
        assertEquals(11, result.questionCount)
        assertEquals(emptyList<String>(), result.dimensions.map { it.id })
    }

    @Test
    fun `todo Nunca produce puntaje 0 y nivel Variable sin dimensiones`() {
        // §5.5: todas «Nunca» → puntaje 0, ninguna bandera, ninguna dimensión.
        val answers = puntuables.associateWith { if (it == "p11") "r0" else "f0" }

        val result = useCase(instrument, answers)

        assertEquals(0, result.score)
        assertEquals(11, result.answeredCount)
        assertEquals("variable", result.level.id)
        assertEquals(emptyList<String>(), result.dimensions.map { it.id })
    }

    @Test
    fun `la pregunta de contexto P0 no puntua ni cuenta`() {
        val result = useCase(instrument, mapOf("p0" to "k1", "p1" to "f3"))

        assertEquals(3, result.score)
        assertEquals(1, result.answeredCount)
        assertEquals(11, result.questionCount)
    }

    // ---- Track 1: tipos encendidos con riesgo bajo ----

    @Test
    fun `tipos encendidos con riesgo bajo se nombran aunque el nivel sea Variable`() {
        // P1 «Una vez» (psicológica) y P4 «Una vez» (económica): puntaje 2.
        val result = useCase(instrument, mapOf("p1" to "f1", "p4" to "f1"))

        assertEquals(2, result.score)
        assertEquals("variable", result.level.id)
        assertEquals(listOf("psico", "economica"), result.dimensions.map { it.id })
    }

    // ---- Track 2: banderas críticas (§5.3) ----

    @Test
    fun `P6 estrangulamiento con una vez impone minimo Grave`() {
        val result = useCase(instrument, mapOf("p6" to "f1"))

        assertEquals(3, result.score)
        assertEquals("grave", result.level.id)
        assertEquals(listOf("fisica"), result.dimensions.map { it.id })
    }

    @Test
    fun `P6 estrangulamiento con varias veces sube a Extremo`() {
        val result = useCase(instrument, mapOf("p6" to "f2"))

        assertEquals(6, result.score)
        assertEquals("extremo", result.level.id)
    }

    @Test
    fun `P7 amenaza con una vez impone minimo Grave`() {
        val result = useCase(instrument, mapOf("p7" to "f1"))

        assertEquals("grave", result.level.id)
    }

    @Test
    fun `P7 amenaza en el maximo fuerza Extremo aunque el resto sea Nunca`() {
        // Bandera crítica forzada: P7=3 → Extremo; P7 no enciende dimensión.
        val result = useCase(instrument, mapOf("p7" to "f3"))

        assertEquals(9, result.score)
        assertEquals("extremo", result.level.id)
        assertEquals(emptyList<String>(), result.dimensions.map { it.id })
    }

    @Test
    fun `P8 violencia sexual con una vez impone minimo Grave`() {
        val result = useCase(instrument, mapOf("p8" to "f1"))

        assertEquals(3, result.score)
        assertEquals("grave", result.level.id)
        assertEquals(listOf("sexual"), result.dimensions.map { it.id })
    }

    @Test
    fun `P11 miedo todo el tiempo impone minimo Grave`() {
        val result = useCase(instrument, mapOf("p11" to "r3"))

        assertEquals(6, result.score)
        assertEquals("grave", result.level.id)
    }

    @Test
    fun `P11 miedo con frecuencia no dispara la bandera`() {
        // La bandera de P11 exige el máximo (=3); «Con frecuencia» (2) no.
        val result = useCase(instrument, mapOf("p11" to "r2"))

        assertEquals(4, result.score)
        assertEquals("variable", result.level.id)
    }

    @Test
    fun `dos banderas criticas activas suben a Extremo`() {
        // P6 y P7 «Una vez»: cada una sola daría Grave; juntas, la regla 2+ → Extremo.
        val result = useCase(instrument, mapOf("p6" to "f1", "p7" to "f1"))

        assertEquals(6, result.score)
        assertEquals("extremo", result.level.id)
    }

    @Test
    fun `una sola bandera critica no dispara la regla de dos o mas`() {
        val result = useCase(instrument, mapOf("p8" to "f1"))

        assertEquals("grave", result.level.id)
    }

    @Test
    fun `la bandera nunca baja un nivel base superior`() {
        // Puntaje 46 (base Extremo) con P8 activo (bandera Grave): sigue Extremo.
        val answers = mapOf(
            "p1" to "f3", "p2" to "f3", "p3" to "f3", "p4" to "f3", "p5" to "f3",
            "p9" to "f3", "p10" to "f3", "p11" to "r2", "p8" to "f3"
        )

        val result = useCase(instrument, answers)

        assertEquals(46, result.score)
        assertEquals("extremo", result.level.id)
    }

    @Test
    fun `todo en el maximo da puntaje 66 y Extremo`() {
        val answers = puntuables.associateWith { if (it == "p11") "r3" else "f3" }

        val result = useCase(instrument, answers)

        assertEquals(66, result.score)
        assertEquals("extremo", result.level.id)
        assertEquals(listOf("psico", "fisica", "sexual", "economica"), result.dimensions.map { it.id })
    }

    // ---- Robustez (§3) ----

    @Test
    fun `una opcion desconocida cuenta como no respondida`() {
        val result = useCase(instrument, mapOf("p1" to "zz", "p2" to "f2"))

        assertEquals(2, result.score)
        assertEquals(1, result.answeredCount)
    }

    @Test
    fun `una respuesta a una pregunta desconocida se ignora`() {
        val result = useCase(instrument, mapOf("qX" to "f3"))

        assertEquals(0, result.score)
        assertEquals(0, result.answeredCount)
        assertEquals("variable", result.level.id)
    }

    @Test
    fun `una bandera hacia un nivel inexistente no rompe el calculo`() {
        val dangling = instrument.copy(
            thresholdFlags = listOf(ThresholdFlag("p6", 1, "no-existe")),
            countFlags = emptyList()
        )

        val result = useCase(dangling, mapOf("p6" to "f2"))

        assertEquals(6, result.score)
        assertEquals("variable", result.level.id)
    }

    // ---------------------------------------------------------------------
    // Bordes exactos de umbral (§5.3) sobre un fixture sin banderas: aísla la
    // capa de clasificación por puntaje del pseudocódigo (§5.4).
    // ---------------------------------------------------------------------

    @Test
    fun `bordes exactos de los cuatro umbrales`() {
        val thresholds = thresholdsInstrument()

        assertEquals("variable", useCase(thresholds, answersForScore(9)).level.id)
        assertEquals("moderado", useCase(thresholds, answersForScore(10)).level.id)
        assertEquals("moderado", useCase(thresholds, answersForScore(24)).level.id)
        assertEquals("grave", useCase(thresholds, answersForScore(25)).level.id)
        assertEquals("grave", useCase(thresholds, answersForScore(40)).level.id)
        assertEquals("extremo", useCase(thresholds, answersForScore(41)).level.id)
        assertEquals("extremo", useCase(thresholds, answersForScore(66)).level.id)
    }

    // ---- Helpers ----

    private val puntuables = listOf("p1", "p2", "p3", "p4", "p5", "p8", "p6", "p7", "p9", "p10", "p11")

    private fun level(id: String, order: Int, minScore: Int) = GuidanceLevel(
        id = id,
        order = order,
        minScore = minScore,
        title = "Nivel $id",
        summary = "Resumen",
        advice = listOf("Consejo"),
        lineIds = emptyList(),
        agencyIds = emptyList(),
        municipalAgencyIds = emptyMap()
    )

    /** 22 preguntas de peso 1 con escala 0–3 y sin banderas: puntaje = suma de valores (0..66). */
    private fun thresholdsInstrument(): Questionnaire {
        val scale = Scale(
            "s",
            listOf(
                ScaleOption("v0", 1, "0", 0),
                ScaleOption("v1", 2, "1", 1),
                ScaleOption("v2", 3, "2", 2),
                ScaleOption("v3", 4, "3", 3)
            )
        )
        val questions = (1..22).map { Question("t$it", it, "a", "s", "q$it", weight = 1) }
        return Questionnaire(
            id = "syn",
            version = 1,
            title = "t",
            intro = "i",
            notice = "n",
            scales = listOf(scale),
            blocks = listOf(Block("a", 1, "A")),
            questions = questions,
            dimensions = listOf(Dimension("d", 1, "x", emptyList(), 1)),
            recognition = Recognition("p", "e"),
            thresholdFlags = emptyList(),
            countFlags = emptyList(),
            municipalities = listOf(Municipality("m", 1, "M")),
            levels = listOf(
                level("variable", 1, 0),
                level("moderado", 2, 10),
                level("grave", 3, 25),
                level("extremo", 4, 41)
            )
        )
    }

    private fun answersForScore(target: Int): Map<String, String> {
        val answers = HashMap<String, String>()
        var remaining = target
        var index = 1
        while (remaining > 0 && index <= 22) {
            val value = minOf(3, remaining)
            answers["t$index"] = "v$value"
            remaining -= value
            index++
        }
        return answers
    }
}
