package com.auroraclima.app.questionnaire.ui

import androidx.annotation.StringRes
import com.auroraclima.app.core.directory.DirectoryAccess
import com.auroraclima.app.core.directory.DirectoryEntity
import com.auroraclima.app.core.directory.DirectoryLine
import com.auroraclima.app.questionnaire.domain.model.Block
import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.ScaleOption

/** Fase del flujo: portada → una pregunta por pantalla → resultado. */
enum class GuidancePhase { INTRO, QUESTION, RESULT }

/**
 * Estado del cuestionario orientador validado (RF-27). Las respuestas viven
 * SOLO aquí, en memoria: no hay SavedStateHandle, ni rememberSaveable, ni
 * repositorio alguno que las reciba — abandonar o morir el proceso no deja nada
 * en disco (RF-27 criterio 2, base de RF-28).
 *
 * Los accesos ([access]) se resuelven contra content al llegar al resultado; el
 * municipio elegido ([selectedMunicipalityId]) solo filtra en memoria las
 * entidades ya resueltas, sin nuevas consultas.
 */
data class GuidanceUiState(
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
    val questionnaire: Questionnaire? = null,
    val phase: GuidancePhase = GuidancePhase.INTRO,
    val questionIndex: Int = 0,
    /** id de pregunta → id de la opción elegida. */
    val answers: Map<String, String> = emptyMap(),
    val result: GuidanceResult? = null,
    val selectedMunicipalityId: String? = null,
    val access: DirectoryAccess? = null,
    /** Hoja de confirmación del guardado (RF-31 criterio 2) visible. */
    val isSaveSheetVisible: Boolean = false,
    val isSaving: Boolean = false,
    /** El resultado visible ya se guardó: el botón cede su lugar a la constancia. */
    val isSaved: Boolean = false,
    @StringRes val saveErrorRes: Int? = null
) {
    val currentQuestion: Question?
        get() = questionnaire?.questions?.getOrNull(questionIndex)

    /** Bloque al que pertenece la pregunta visible, para el encabezado (§3 del documento). */
    val currentBlock: Block?
        get() {
            val question = currentQuestion ?: return null
            return questionnaire?.blocks?.firstOrNull { it.id == question.blockId }
        }

    /** Opciones de la escala de la pregunta visible (frecuencia, percepción o vínculo). */
    val currentOptions: List<ScaleOption>
        get() {
            val question = currentQuestion ?: return emptyList()
            return questionnaire?.scales?.firstOrNull { it.id == question.scaleId }?.options ?: emptyList()
        }

    /** Respuesta de la pregunta visible, si la usuaria eligió alguna. */
    val selectedOptionId: String?
        get() = currentQuestion?.let { answers[it.id] }

    val isLastQuestion: Boolean
        get() = questionnaire != null && questionIndex == questionnaire.questions.lastIndex

    /**
     * Línea de reconocimiento de tipos (§6.1): nombra las dimensiones encendidas
     * o, si no hay ninguna, el texto de ausencia. Los fragmentos y el texto de
     * ausencia son literales del documento (viven en el cuestionario, no en el APK).
     * La UI resalta los nombres con [emphasizedRecognition]; el texto es el mismo.
     */
    val recognition: RecognitionLine?
        get() {
            val recognition = questionnaire?.recognition ?: return null
            val dimensions = result?.dimensions ?: return null
            return RecognitionLine(
                prefix = recognition.prefix,
                labels = dimensions.map { it.label },
                empty = recognition.empty
            )
        }

    /** Líneas nacionales del nivel (siempre visibles), ya resueltas y en orden. */
    val nationalLines: List<DirectoryLine>
        get() = access?.lines ?: emptyList()

    /** Entidades fijas del nivel (no dependen del municipio). */
    val fixedEntities: List<DirectoryEntity>
        get() = entitiesFor(result?.level?.agencyIds)

    /** Entidades del municipio elegido; vacío hasta que la usuaria elige uno. */
    val municipalEntities: List<DirectoryEntity>
        get() {
            val municipality = selectedMunicipalityId ?: return emptyList()
            return entitiesFor(result?.level?.municipalAgencyIds?.get(municipality))
        }

    private fun entitiesFor(ids: List<String>?): List<DirectoryEntity> {
        if (ids.isNullOrEmpty()) return emptyList()
        val byId = access?.entities?.associateBy { it.id } ?: return emptyList()
        return ids.mapNotNull { byId[it] }
    }
}
