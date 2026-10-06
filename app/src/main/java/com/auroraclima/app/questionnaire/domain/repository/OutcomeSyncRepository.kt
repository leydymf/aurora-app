package com.auroraclima.app.questionnaire.domain.repository

/**
 * Sincronización cifrada de los resultados guardados (RF-31 + RF-39): mismo
 * contrato que la sync del historial — [push] y [remove] son de mejor esfuerzo
 * sobre la cola durable del SDK; [restore] repone en un dispositivo nuevo, solo
 * sobre un almacén vacío.
 */
interface OutcomeSyncRepository {
    suspend fun push(outcomeId: Long): Result<Unit>

    suspend fun remove(docId: String): Result<Unit>

    suspend fun restore(): Result<Unit>

    /**
     * Hace converger los resultados guardados con la nube (ADR 2026-10-06):
     * trae los que se guardaron en otro dispositivo y elimina aquí los que allá
     * se eliminaron. Un resultado no se edita nunca, así que no hay empates.
     */
    suspend fun reconcile(): Result<Unit>
}
