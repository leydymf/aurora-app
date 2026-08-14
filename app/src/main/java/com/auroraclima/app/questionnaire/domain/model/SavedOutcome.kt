package com.auroraclima.app.questionnaire.domain.model

import java.time.Instant

/**
 * Un resultado del cuestionario guardado por decisión expresa de la usuaria
 * (RF-31; ADR 2026-08-14). Minimización estricta (RNF-04): fecha del guardado,
 * orden y título del nivel y etiquetas de los tipos reconocidos — JAMÁS las
 * respuestas individuales ni el puntaje. [rank] y [label] son un snapshot del
 * nivel al momento de guardar: el documento administrable puede cambiar de
 * versión después y el resultado guardado debe seguir leyéndose igual.
 */
data class SavedOutcome(
    val id: Long,
    val savedAt: Instant,
    val rank: Int,
    val label: String,
    val tags: List<String>
)

/** Lo que se persiste al confirmar el guardado; la fecha la pone el repositorio. */
data class NewSavedOutcome(
    val rank: Int,
    val label: String,
    val tags: List<String>
)
