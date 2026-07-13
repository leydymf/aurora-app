package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio del cuestionario sobre la fuente remota (RF-27). Filtra
 * esquemas no soportados (ARQUITECTURA §6), descarta configuraciones
 * incompletas (espejo de RF-43) y elige la de mayor versión de publicación
 * (RF-44). Todo fallo se traduce a [Result]: ninguna excepción cruza a la
 * UI (§3) y nada se registra en logs (regla 4 de CLAUDE.md).
 */
@Singleton
class FirestoreQuestionnaireRepository @Inject constructor(
    private val remote: QuestionnaireRemoteSource
) : QuestionnaireRepository {
    override suspend fun getQuestionnaire(): Result<Questionnaire> = withContext(Dispatchers.IO) {
        try {
            val questionnaire = remote.fetchForms()
                .filter { it.schemaVersion.isSupportedSchema() }
                .mapNotNull { it.toDomain() }
                .maxByOrNull { it.version }
            if (questionnaire != null) {
                Result.success(questionnaire)
            } else {
                // Sin mensaje: no hay nada que filtrar hacia la UI (P-24).
                Result.failure(NoSuchElementException())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
