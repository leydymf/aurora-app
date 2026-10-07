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

    /** Si se fija, toda subida después de las N primeras lanza (interrupción a mitad, RF-39). */
    var failAfterUploads: Int? = null

    val uploaded = mutableListOf<Pair<String, EncryptedEntryDto>>()
    val queued = mutableListOf<Pair<String, EncryptedEntryDto>>()
    val deleted = mutableListOf<String>()
    val queuedDeletes = mutableListOf<String>()

    override suspend fun upload(documentId: String, document: EncryptedEntryDto) {
        failAfterUploads?.let { limit -> if (uploaded.size >= limit) throw IllegalStateException() }
        val write = documentId to document
        if (online) {
            uploaded += write
            upsertRemote(write)
        } else {
            queued += write
        }
    }

    override suspend fun delete(documentId: String) {
        if (online) {
            deleted += documentId
            remoteDocs.removeAll { it.first == documentId }
        } else {
            queuedDeletes += documentId
        }
    }

    /**
     * Documentos «en el servidor» que servirá [downloadAll] (restauración
     * RF-39): las subidas en línea lo actualizan, como el servidor real.
     */
    val remoteDocs = mutableListOf<Pair<String, EncryptedEntryDto?>>()

    private fun upsertRemote(write: Pair<String, EncryptedEntryDto?>) {
        remoteDocs.removeAll { it.first == write.first }
        remoteDocs += write
    }

    override suspend fun downloadAll(): List<Pair<String, EncryptedEntryDto?>> {
        // Es una lectura del servidor: sin red, lanza (como Source.SERVER).
        if (!online) throw IllegalStateException()
        return remoteDocs.toList()
    }

    /** Reconexión: la cola durable se aplica automáticamente (RF-38). */
    fun reconnect() {
        online = true
        queued.forEach { upsertRemote(it) }
        uploaded += queued
        queued.clear()
        queuedDeletes.forEach { id -> remoteDocs.removeAll { it.first == id } }
        deleted += queuedDeletes
        queuedDeletes.clear()
    }
}
