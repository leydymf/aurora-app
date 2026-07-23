package com.auroraclima.app.questionnaire.domain.model

/**
 * Uno de los dos bloques visibles del cuestionario (documento validado §3):
 * A «Lo que estás viviendo» y B «Tu seguridad». La división cumple una función
 * de acompañamiento emocional —separa el reconocimiento de lo que se vive de
 * las preguntas más difíciles de seguridad— sin duplicar ninguna pregunta.
 */
data class Block(
    val id: String,
    val order: Int,
    val title: String
)
