package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.BlockDto
import com.auroraclima.app.questionnaire.data.remote.CountFlagDto
import com.auroraclima.app.questionnaire.data.remote.DimensionDto
import com.auroraclima.app.questionnaire.data.remote.FormDto
import com.auroraclima.app.questionnaire.data.remote.LevelDto
import com.auroraclima.app.questionnaire.data.remote.MunicipalityDto
import com.auroraclima.app.questionnaire.data.remote.QuestionDto
import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.data.remote.RecognitionDto
import com.auroraclima.app.questionnaire.data.remote.ScaleDto
import com.auroraclima.app.questionnaire.data.remote.ScaleOptionDto
import com.auroraclima.app.questionnaire.data.remote.ThresholdFlagDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El repositorio filtra esquemas no soportados (ARQUITECTURA §6), descarta
 * el documento COMPLETO ante cualquier pieza estructural inválida (espejo de
 * RF-43), elige la mayor versión de publicación (RF-44) y traduce todo fallo a
 * [Result]: ninguna excepción cruza hacia la UI (§3). Sin conexión aplica el
 * mismo contrato de caché persistente probado para content (RF-26).
 */
class FirestoreQuestionnaireRepositoryTest {
    private val remote = FakeQuestionnaireRemoteSource()
    private val repository = FirestoreQuestionnaireRepository(remote)

    @Test
    fun `un documento valido llega mapeado y ordenado`() = runTest {
        // Entradas deliberadamente desordenadas: rige el campo order.
        remote.forms = listOf(
            formDto(
                scales = listOf(
                    scaleDto(
                        "freq",
                        listOf(scaleOptionDto("s2", 3, 2), scaleOptionDto("s0", 1, 0))
                    )
                ),
                blocks = listOf(blockDto("b", 2), blockDto("a", 1)),
                questions = listOf(questionDto("q02", 2), questionDto("q01", 1)),
                dimensions = listOf(dimensionDto("d2", 2), dimensionDto("d1", 1)),
                municipalities = listOf(municipalityDto("m2", 2), municipalityDto("m1", 1)),
                levels = listOf(levelDto("n02", 2, 4), levelDto("n01", 1, 0))
            )
        )

        val questionnaire = repository.getQuestionnaire().getOrThrow()

        assertEquals(listOf("s0", "s2"), questionnaire.scales.single().options.map { it.id })
        assertEquals(listOf("a", "b"), questionnaire.blocks.map { it.id })
        assertEquals(listOf("q01", "q02"), questionnaire.questions.map { it.id })
        assertEquals(listOf("d1", "d2"), questionnaire.dimensions.map { it.id })
        assertEquals(listOf("m1", "m2"), questionnaire.municipalities.map { it.id })
        assertEquals(listOf("n01", "n02"), questionnaire.levels.map { it.id })
        assertEquals("Aviso", questionnaire.notice)
    }

    @Test
    fun `un esquema futuro se descarta`() = runTest {
        // ARQUITECTURA §6: el panel podrá publicar esquemas nuevos sin tumbar la app.
        remote.forms = listOf(formDto(schemaVersion = 3), formDto(schemaVersion = null))

        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `entre varios documentos validos rige la mayor version`() = runTest {
        remote.forms = listOf(
            formDto(id = "f01", version = 1),
            formDto(id = "f02", version = 3),
            formDto(id = "f03", version = 2)
        )

        assertEquals("f02", repository.getQuestionnaire().getOrThrow().id)
    }

    @Test
    fun `un documento invalido no rige aunque tenga la mayor version`() = runTest {
        // RF-43/RF-44: una publicación incompleta jamás sustituye a la vigente.
        remote.forms = listOf(
            formDto(id = "vigente", version = 1),
            formDto(id = "roto", version = 2, questions = emptyList())
        )

        assertEquals("vigente", repository.getQuestionnaire().getOrThrow().id)
    }

    @Test
    fun `un esquema desconocido no sustituye a la version vigente`() = runTest {
        // RF-25: si el panel publicara un esquema que esta app no interpreta,
        // sigue rigiendo el último formulario válido presente en la colección.
        remote.forms = listOf(
            formDto(id = "vigente", version = 2),
            formDto(id = "nuevo", version = 3, schemaVersion = 3)
        )

        assertEquals("vigente", repository.getQuestionnaire().getOrThrow().id)
    }

    @Test
    fun `sin piezas obligatorias el documento se descarta`() = runTest {
        // Espejo del criterio 2 de RF-43: cada pieza estructural es obligatoria.
        remote.forms = listOf(formDto(questions = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(levels = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(scales = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(blocks = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(dimensions = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(municipalities = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `sin aviso o sin reconocimiento el documento se descarta`() = runTest {
        // RF-30: sin aviso de alcance no hay resultado; sin reconocimiento no hay Track 1.
        remote.forms = listOf(formDto(notice = null))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(recognition = null))
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `una pieza embebida invalida descarta el documento completo`() = runTest {
        // A diferencia de content: omitir solo la pregunta rota sesgaría el
        // puntaje frente a umbrales calibrados para el conjunto.
        remote.forms = listOf(
            formDto(questions = listOf(questionDto("q01", 1), questionDto("q02", 2, text = null)))
        )
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(levels = listOf(levelDto("n01", 1, null))))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(
            formDto(scales = listOf(scaleDto("freq", listOf(scaleOptionDto("s0", 1, null)))))
        )
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `una pregunta con bloque o escala inexistente descarta el documento`() = runTest {
        // Estructural: sin bloque o escala válidos la pregunta no se puede pintar ni puntuar.
        remote.forms = listOf(formDto(questions = listOf(questionDto("q01", 1, blockId = "no-existe"))))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(questions = listOf(questionDto("q01", 1, scaleId = "no-existe"))))
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `una referencia huerfana en dimensiones o banderas no descarta el documento`() = runTest {
        // Tolerancia (como RF-42): el motor trata la pregunta ausente como 0 y
        // el nivel ausente como sin bandera; el panel es quien lo impide publicar.
        remote.forms = listOf(
            formDto(
                dimensions = listOf(dimensionDto("d1", 1, questionIds = listOf("q01", "no-existe"))),
                thresholdFlags = listOf(ThresholdFlagDto("no-existe", 1, "no-existe")),
                countFlags = listOf(CountFlagDto(listOf("q01", "no-existe"), 1, 2, "n01"))
            )
        )

        val questionnaire = repository.getQuestionnaire().getOrThrow()

        assertEquals(listOf("q01", "no-existe"), questionnaire.dimensions.single().questionIds)
        assertEquals(1, questionnaire.thresholdFlags.size)
    }

    @Test
    fun `una coleccion vacia es un Result de fallo sin mensaje`() = runTest {
        remote.forms = emptyList()

        val result = repository.getQuestionnaire()

        assertTrue(result.isFailure)
        // P-24: nada que pueda filtrarse hacia la UI.
        assertNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `un fallo de la fuente remota es un Result de fallo`() = runTest {
        // §3: la excepción no cruza. Es también la primera apertura sin red
        // (caché vacía): la UI mostrará el genérico con reintento (RF-26).
        remote.nextError = IOException("sin red")

        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `la cancelacion si se propaga`() = runTest {
        remote.nextError = CancellationException("cancelada")

        var propagated = false
        try {
            repository.getQuestionnaire()
        } catch (expected: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}

private class FakeQuestionnaireRemoteSource : QuestionnaireRemoteSource {
    var nextError: Exception? = null
    var forms = emptyList<FormDto>()

    override suspend fun fetchForms(): List<FormDto> {
        nextError?.let {
            nextError = null
            throw it
        }
        return forms
    }
}

private fun formDto(
    id: String = "f01",
    schemaVersion: Long? = 2,
    version: Long? = 1,
    title: String? = "Título",
    intro: String? = "Intro",
    notice: String? = "Aviso",
    scales: List<ScaleDto> = listOf(scaleDto()),
    blocks: List<BlockDto> = listOf(blockDto()),
    questions: List<QuestionDto> = listOf(questionDto("q01", 1)),
    dimensions: List<DimensionDto> = listOf(dimensionDto()),
    recognition: RecognitionDto? = RecognitionDto("Prefijo", "Vacío"),
    thresholdFlags: List<ThresholdFlagDto> = emptyList(),
    countFlags: List<CountFlagDto> = emptyList(),
    municipalities: List<MunicipalityDto> = listOf(municipalityDto()),
    levels: List<LevelDto> = listOf(levelDto("n01", 1, 0), levelDto("n02", 2, 4))
) = FormDto(
    id, schemaVersion, version, title, intro, notice, scales, blocks, questions,
    dimensions, recognition, thresholdFlags, countFlags, municipalities, levels
)

private fun scaleDto(
    id: String? = "freq",
    options: List<ScaleOptionDto> = listOf(scaleOptionDto("s0", 1, 0), scaleOptionDto("s1", 2, 1))
) = ScaleDto(id, options)

private fun scaleOptionDto(id: String?, order: Long?, value: Long?, label: String? = "Opción") =
    ScaleOptionDto(id, order, label, value)

private fun blockDto(id: String? = "a", order: Long? = 1, title: String? = "Bloque") = BlockDto(id, order, title)

private fun questionDto(
    id: String?,
    order: Long?,
    blockId: String? = "a",
    scaleId: String? = "freq",
    text: String? = "Pregunta $id",
    weight: Long? = 1,
    context: Boolean = false
) = QuestionDto(id, order, blockId, scaleId, text, weight, context)

private fun dimensionDto(
    id: String? = "d01",
    order: Long? = 1,
    label: String? = "violencia psicológica",
    questionIds: List<String> = listOf("q01"),
    minValue: Long? = 1
) = DimensionDto(id, order, label, questionIds, minValue)

private fun municipalityDto(id: String? = "m01", order: Long? = 1, label: String? = "Bucaramanga") =
    MunicipalityDto(id, order, label)

private fun levelDto(
    id: String?,
    order: Long?,
    minScore: Long?,
    title: String? = "Nivel $id",
    summary: String? = "Resumen"
) = LevelDto(
    id = id,
    order = order,
    minScore = minScore,
    title = title,
    summary = summary,
    advice = listOf("Consejo"),
    lineIds = emptyList(),
    agencyIds = emptyList(),
    municipalAgencyIds = emptyMap()
)
