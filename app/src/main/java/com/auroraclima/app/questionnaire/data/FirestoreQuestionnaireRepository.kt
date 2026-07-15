package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio del cuestionario sobre la fuente remota (RF-27). Filtra
 * esquemas no soportados (ARQUITECTURA §6), descarta configuraciones
 * incompletas (espejo de RF-43) y elige la de mayor versión de publicación
 * (RF-44). Todo fallo se traduce a [Result] con `networkResult` (§3, P-39):
 * ninguna excepción cruza a la UI y nada se registra en logs (regla 4).
 */
@Singleton
class FirestoreQuestionnaireRepository @Inject constructor(
    private val remote: QuestionnaireRemoteSource
) : QuestionnaireRepository {
    override suspend fun getQuestionnaire(): Result<Questionnaire> = networkResult {
        // Sin config válida: NoSuchElementException → failure, nada que filtrar hacia la UI (P-24).
        remote.fetchForms()
            .filter { it.schemaVersion.isSupportedSchema() }
            .mapNotNull { it.toDomain() }
            .maxByOrNull { it.version }
            ?: throw NoSuchElementException()
    }
}
