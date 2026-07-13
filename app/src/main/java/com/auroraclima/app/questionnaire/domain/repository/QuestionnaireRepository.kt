package com.auroraclima.app.questionnaire.domain.repository

import com.auroraclima.app.questionnaire.domain.model.Questionnaire

/**
 * Acceso al cuestionario administrable (RF-27; publicado según RF-43/RF-44).
 * Devuelve [Result]; las excepciones no cruzan a la UI (ARQUITECTURA §3).
 * La disponibilidad sin conexión la aporta la caché persistente de la
 * fuente de datos (mismo contrato de RF-26 que el módulo de información).
 */
interface QuestionnaireRepository {
    /**
     * La configuración vigente: el documento válido de mayor versión de
     * publicación. Sin documento válido (colección vacía, esquemas futuros
     * o configuraciones incompletas) el resultado es un fallo.
     */
    suspend fun getQuestionnaire(): Result<Questionnaire>
}
