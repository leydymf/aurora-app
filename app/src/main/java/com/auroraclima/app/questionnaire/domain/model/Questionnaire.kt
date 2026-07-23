package com.auroraclima.app.questionnaire.domain.model

/**
 * Configuración completa del cuestionario orientador validado (RF-27, RF-29),
 * publicada como un único documento administrable (base de RF-43/RF-44).
 *
 * Reúne las dos vías del instrumento (documento validado §5): el reconocimiento
 * de tipos ([dimensions] + [recognition], Track 1) y el nivel de riesgo
 * ([levels] + banderas, Track 2). Las preguntas se agrupan en [blocks] y cada
 * una responde con las opciones de su [scales].
 *
 * Invariantes en construcción: sin escalas, bloques, preguntas, dimensiones,
 * municipios o niveles el cuestionario no puede existir — el tipo encarna el
 * criterio 2 de RF-43 («una configuración incompleta no puede publicarse»). El
 * mapper de data descarta el documento COMPLETO si cualquier pieza falta.
 */
data class Questionnaire(
    val id: String,
    /** Número de publicación: entre documentos válidos rige el más alto (RF-44). */
    val version: Int,
    val title: String,
    val intro: String,
    /** Aviso de alcance orientador (RF-30), visible en todo resultado (§6.1, literal). */
    val notice: String,
    val scales: List<Scale>,
    val blocks: List<Block>,
    val questions: List<Question>,
    val dimensions: List<Dimension>,
    /** Textos de la línea de reconocimiento de tipos (§6.1). */
    val recognition: Recognition,
    val thresholdFlags: List<ThresholdFlag>,
    val countFlags: List<CountFlag>,
    val municipalities: List<Municipality>,
    val levels: List<GuidanceLevel>
) {
    init {
        require(scales.isNotEmpty()) { "Las escalas no pueden estar vacías" }
        require(blocks.isNotEmpty()) { "Los bloques no pueden estar vacíos" }
        require(questions.isNotEmpty()) { "Las preguntas no pueden estar vacías" }
        require(dimensions.isNotEmpty()) { "Las dimensiones no pueden estar vacías" }
        require(municipalities.isNotEmpty()) { "Los municipios no pueden estar vacíos" }
        require(levels.isNotEmpty()) { "Los niveles no pueden estar vacíos" }
    }
}
