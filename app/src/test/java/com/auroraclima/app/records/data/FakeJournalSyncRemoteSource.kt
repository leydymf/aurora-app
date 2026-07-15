package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.remote.EncryptedEntryDto
import com.auroraclima.app.records.data.remote.JournalSyncRemoteSource

/**
 * Fake de la fuente remota que reproduce la cola offline→online del SDK: sin
 * conexión las subidas quedan encoladas y [reconnect] las vuelca. Captura los
 * DTOs para inspeccionar que el payload jamás viaje en claro.
 */
class FakeJournalSyncRemoteSource : JournalSyncRemoteSource {
    var online = true
    val uploaded = mutableListOf<Pair<String, EncryptedEntryDto>>()
    val queued = mutableListOf<Pair<String, EncryptedEntryDto>>()
    val deleted = mutableListOf<String>()

    override suspend fun upload(documentId: String, document: EncryptedEntryDto) {
        val write = documentId to document
        if (online) uploaded += write else queued += write
    }

    override suspend fun delete(documentId: String) {
        deleted += documentId
    }

    /** Reconexión: la cola durable se sube automáticamente (RF-38). */
    fun reconnect() {
        online = true
        uploaded += queued
        queued.clear()
    }
}
