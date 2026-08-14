package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.EncryptedOutcomeDto
import com.auroraclima.app.questionnaire.data.remote.OutcomeSyncRemoteSource

/**
 * Fake de la fuente remota de `outcomes` (RF-31): captura los DTOs para
 * inspeccionar que el payload jamás viaje en claro y sirve un «servidor» en
 * memoria para la restauración (mismo criterio que el fake del historial).
 */
class FakeOutcomeSyncRemoteSource : OutcomeSyncRemoteSource {
    val uploaded = mutableListOf<Pair<String, EncryptedOutcomeDto>>()
    val deleted = mutableListOf<String>()

    /** Documentos «en el servidor» que servirá [downloadAll] (restauración RF-39). */
    val remoteDocs = mutableListOf<Pair<String, EncryptedOutcomeDto>>()

    override suspend fun upload(documentId: String, document: EncryptedOutcomeDto) {
        uploaded += documentId to document
        remoteDocs.removeAll { it.first == documentId }
        remoteDocs += documentId to document
    }

    override suspend fun delete(documentId: String) {
        deleted += documentId
        remoteDocs.removeAll { it.first == documentId }
    }

    override suspend fun downloadAll(): List<Pair<String, EncryptedOutcomeDto>> = remoteDocs.toList()
}
