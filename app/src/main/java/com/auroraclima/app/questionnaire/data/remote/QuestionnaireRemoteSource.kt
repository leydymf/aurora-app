package com.auroraclima.app.questionnaire.data.remote

/**
 * Fuente remota del cuestionario (mismo rol que `ContentRemoteSource` en
 * content): el repositorio se prueba con un fake sin conocer el SDK.
 * Lanza en fallo; el repositorio traduce a [Result].
 */
interface QuestionnaireRemoteSource {
    suspend fun fetchForms(): List<FormDto>
}
