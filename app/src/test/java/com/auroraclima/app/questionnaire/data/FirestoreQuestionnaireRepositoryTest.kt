package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.FormDto
import com.auroraclima.app.questionnaire.data.remote.LevelDto
import com.auroraclima.app.questionnaire.data.remote.QuestionDto
import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.data.remote.ScaleOptionDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El repositorio filtra esquemas no soportados (ARQUITECTURA §6), descarta
 * el documento COMPLETO ante cualquier pieza inválida (espejo de RF-43),
 * elige la mayor versión de publicación (RF-44) y traduce todo fallo a
 * [Result]: ninguna excepción cruza hacia la UI (§3). Sin conexión aplica
 * el mismo contrato de caché persistente probado para content (RF-26).
 */
class FirestoreQuestionnaireRepositoryTest {
    private val remote = FakeQuestionnaireRemoteSource()
    private val repository = FirestoreQuestionnaireRepository(remote)

    @Test
    fun `un documento valido llega mapeado y ordenado`() = runTest {
        // Entradas deliberadamente desordenadas: rige el campo order.
        remote.forms = listOf(
            formDto(
                scale = listOf(scaleDto("s2", order = 3, value = 2), scaleDto("s0", order = 1, value = 0)),
                questions = listOf(questionDto("q02", order = 2), questionDto("q01", order = 1)),
                levels = listOf(levelDto("n02", order = 2, minScore = 4), levelDto("n01", order = 1, minScore = 0))
            )
        )

        val questionnaire = repository.getQuestionnaire().getOrThrow()

        assertEquals(listOf("s0", "s2"), questionnaire.scale.map { it.id })
        assertEquals(listOf("q01", "q02"), questionnaire.questions.map { it.id })
        assertEquals(listOf("n01", "n02"), questionnaire.levels.map { it.id })
        assertEquals("Aviso", questionnaire.notice)
    }

    @Test
    fun `un esquema futuro se descarta`() = runTest {
        // ARQUITECTURA §6: el panel podrá publicar esquemas nuevos sin tumbar la app.
        remote.forms = listOf(formDto(schemaVersion = 2), formDto(schemaVersion = null))

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
            formDto(id = "nuevo", version = 3, schemaVersion = 2)
        )

        assertEquals("vigente", repository.getQuestionnaire().getOrThrow().id)
    }

    @Test
    fun `sin preguntas o sin niveles el documento se descarta`() = runTest {
        // Espejo del criterio 2 de RF-43.
        remote.forms = listOf(formDto(questions = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(levels = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(scale = emptyList()))
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `sin aviso el documento se descarta`() = runTest {
        // RF-30: sin aviso de alcance no hay resultado que mostrar.
        remote.forms = listOf(formDto(notice = null))

        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `una pieza embebida invalida descarta el documento completo`() = runTest {
        // A diferencia de content: omitir solo la pregunta rota sesgaría el
        // puntaje frente a umbrales calibrados para el conjunto.
        remote.forms = listOf(
            formDto(questions = listOf(questionDto("q01", order = 1), questionDto("q02", order = 2, text = null)))
        )
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(levels = listOf(levelDto("n01", order = 1, minScore = null))))
        assertTrue(repository.getQuestionnaire().isFailure)

        remote.forms = listOf(formDto(scale = listOf(scaleDto("s0", order = 1, value = null))))
        assertTrue(repository.getQuestionnaire().isFailure)
    }

    @Test
    fun `un alertLevelId resuelto se conserva y uno huerfano se anula`() = runTest {
        remote.forms = listOf(
            formDto(
                questions = listOf(
                    questionDto("q01", order = 1, alertLevelId = "n02"),
                    questionDto("q02", order = 2, alertLevelId = "no-existe")
                )
            )
        )

        val questions = repository.getQuestionnaire().getOrThrow().questions

        assertEquals("n02", questions.first().alertLevelId)
        assertNull(questions.last().alertLevelId)
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
    schemaVersion: Long? = 1,
    version: Long? = 1,
    title: String? = "Título",
    intro: String? = "Intro",
    notice: String? = "Aviso",
    scale: List<ScaleOptionDto> = listOf(scaleDto("s0", order = 1, value = 0), scaleDto("s1", order = 2, value = 1)),
    questions: List<QuestionDto> = listOf(questionDto("q01", order = 1)),
    levels: List<LevelDto> = listOf(levelDto("n01", order = 1, minScore = 0), levelDto("n02", order = 2, minScore = 4))
) = FormDto(id, schemaVersion, version, title, intro, notice, scale, questions, levels)

private fun scaleDto(id: String?, order: Long?, label: String? = "Opción", value: Long?) =
    ScaleOptionDto(id, order, label, value)

private fun questionDto(
    id: String?,
    order: Long?,
    text: String? = "Pregunta $id",
    weight: Long? = 1,
    alertLevelId: String? = null
) = QuestionDto(id, order, text, weight, alertLevelId)

private fun levelDto(
    id: String?,
    order: Long?,
    minScore: Long?,
    title: String? = "Nivel $id",
    summary: String? = "Resumen"
) = LevelDto(id, order, minScore, title, summary, advice = listOf("Consejo"), routeIds = listOf("r01"))
