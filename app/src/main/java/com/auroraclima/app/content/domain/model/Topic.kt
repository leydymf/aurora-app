package com.auroraclima.app.content.domain.model

/** Tema del módulo informativo (RF-21): descripción, señales y ejemplos. */
data class Topic(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val signals: List<String>,
    val examples: List<String>
)
