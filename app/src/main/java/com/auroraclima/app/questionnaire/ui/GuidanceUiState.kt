package com.auroraclima.app.questionnaire.ui

import androidx.annotation.StringRes
import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire

/** Fase del flujo: portada → una pregunta por pantalla → resultado (F6 de A2-6). */
enum class GuidancePhase { INTRO, QUESTION, RESULT }

/**
 * Estado del cuestionario orientador (RF-27, mockups P15–P17). Las respuestas
 * viven SOLO aquí, en memoria: no hay SavedStateHandle, ni rememberSaveable,
 * ni repositorio alguno que las reciba — abandonar o morir el proceso no deja
 * nada en disco (RF-27 criterio 2, base de RF-28).
 */
data class GuidanceUiState(
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
    val questionnaire: Questionnaire? = null,
    val phase: GuidancePhase = GuidancePhase.INTRO,
    val questionIndex: Int = 0,
    /** id de pregunta → id de la opción elegida. */
    val answers: Map<String, String> = emptyMap(),
    val result: GuidanceResult? = null
) {
    val currentQuestion: Question?
        get() = questionnaire?.questions?.getOrNull(questionIndex)

    /** Respuesta de la pregunta visible, si la usuaria eligió alguna. */
    val selectedOptionId: String?
        get() = currentQuestion?.let { answers[it.id] }

    val isLastQuestion: Boolean
        get() = questionnaire != null && questionIndex == questionnaire.questions.lastIndex
}
