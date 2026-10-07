package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.remote.EncryptedMediaHeaderDto
import com.auroraclima.app.records.data.remote.JournalMediaRemoteSource

/**
 * Fake de la fuente remota de evidencias troceadas (RF-39): cabeceras y trozos
 * en memoria, con la misma semántica de cola durable de la sync (las escrituras
 * «entran» siempre; las lecturas exigen red).
 */
class FakeJournalMediaRemoteSource : JournalMediaRemoteSource {
    var online = true

    /** Si se fija, toda subida de adjunto después de las N primeras lanza (interrupción). */
    var failAfterUploads: Int? = null

    val headers = linkedMapOf<String, EncryptedMediaHeaderDto>()
    val parts = linkedMapOf<String, String>()
    val deleted = mutableListOf<String>()

    var uploadCalls = 0
        private set

    override suspend fun upload(ref: String, header: EncryptedMediaHeaderDto, parts: List<String>) {
        failAfterUploads?.let { limit -> if (uploadCalls >= limit) throw IllegalStateException() }
        uploadCalls++
        parts.forEachIndexed { index, data -> this.parts["${ref}_$index"] = data }
        headers[ref] = header
    }

    override suspend fun delete(ref: String) {
        deleted += ref
        headers.remove(ref)
        parts.keys.filter { it.startsWith("${ref}_") }.forEach { parts.remove(it) }
    }

    override suspend fun downloadHeaders(): List<Pair<String, EncryptedMediaHeaderDto?>> {
        if (!online) throw IllegalStateException()
        return headers.map { (ref, header) -> ref to header }
    }

    override suspend fun downloadPart(ref: String, index: Int): String? {
        if (!online) throw IllegalStateException()
        return parts["${ref}_$index"]
    }
}
