package com.auroraclima.app.questionnaire.domain.model

/**
 * Configuración completa del cuestionario orientador (RF-27, RF-29),
 * publicada como un único documento administrable (base de RF-43/RF-44).
 *
 * Invariantes en construcción: sin escala, sin preguntas o sin niveles el
 * cuestionario no puede existir — el tipo encarna el criterio 2 de RF-43
 * («una configuración incompleta no puede publicarse»). El mapper de data
 * descarta esos documentos antes de llegar aquí.
 */
data class Questionnaire(
    val id: String,
    /** Número de publicación: entre documentos válidos rige el más alto (RF-44). */
    val version: Int,
    val title: String,
    val intro: String,
    /** Aviso de alcance orientador (RF-30), visible en todo resultado. */
    val notice: String,
    /** Escala de respuesta compartida por todas las preguntas (decisión F6 de A2-6). */
    val scale: List<ScaleOption>,
    val questions: List<Question>,
    val levels: List<GuidanceLevel>
) {
    init {
        require(scale.isNotEmpty()) { "La escala no puede estar vacía" }
        require(questions.isNotEmpty()) { "Las preguntas no pueden estar vacías" }
        require(levels.isNotEmpty()) { "Los niveles no pueden estar vacíos" }
    }
}
