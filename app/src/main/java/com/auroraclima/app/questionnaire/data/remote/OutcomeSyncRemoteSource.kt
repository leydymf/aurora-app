package com.auroraclima.app.questionnaire.data.remote

/**
 * Escritura remota de los resultados guardados (RF-31). Mismo contrato que la
 * sync del historial: [upload]/[delete] no esperan al servidor (cola durable
 * del SDK, RF-38); [downloadAll] sí — es la lectura de la restauración (RF-39).
 */
interface OutcomeSyncRemoteSource {
    suspend fun upload(documentId: String, document: EncryptedOutcomeDto)

    suspend fun delete(documentId: String)

    /** TODOS los documentos, con DTO nulo si falta algún campo (auditoría 2026-10-06). */
    suspend fun downloadAll(): List<Pair<String, EncryptedOutcomeDto?>>
}

/** Purga de la colección esperando al servidor (RF-20 criterio 2). */
fun interface OutcomePurgeRemoteSource {
    suspend fun deleteAll()
}
