package com.auroraclima.app.records.data.remote

import com.auroraclima.app.core.network.awaitCompletion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escritura y lectura de evidencias troceadas sobre Firestore (RF-39). Como en
 * la sync de registros, las ESCRITURAS no esperan al servidor: quedan en la
 * cola durable del SDK y suben solas al reconectar (RF-38); las LECTURAS de la
 * restauración sí esperan (`Source.SERVER`) — la caché de una instalación
 * nueva está vacía.
 */
@Singleton
class FirestoreJournalMediaRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : JournalMediaRemoteSource {
    override suspend fun upload(ref: String, header: EncryptedMediaHeaderDto, parts: List<String>) {
        parts.forEachIndexed { index, data ->
            partsCollection().document(partId(ref, index)).set(mapOf(FIELD_DATA to data))
        }
        // La cabecera al final: su presencia certifica el adjunto completo.
        mediaCollection().document(ref).set(
            mapOf(
                FIELD_META to header.meta,
                FIELD_IV to header.iv,
                FIELD_PART_COUNT to header.partCount.toLong(),
                FIELD_SCHEMA_VERSION to header.schemaVersion
            )
        )
    }

    override suspend fun delete(ref: String) {
        mediaCollection().document(ref).delete()
        repeat(MAX_BLIND_PARTS) { index ->
            partsCollection().document(partId(ref, index)).delete()
        }
    }

    override suspend fun downloadHeaders(): List<Pair<String, EncryptedMediaHeaderDto?>> =
        mediaCollection().get(Source.SERVER).awaitCompletion().documents.map { document ->
            val meta = document.getString(FIELD_META)
            val iv = document.getString(FIELD_IV)
            val partCount = document.getLong(FIELD_PART_COUNT)
            val schemaVersion = document.getLong(FIELD_SCHEMA_VERSION)
            val header = if (meta != null && iv != null && partCount != null && schemaVersion != null) {
                EncryptedMediaHeaderDto(meta, iv, partCount.toInt(), schemaVersion)
            } else {
                null
            }
            document.id to header
        }

    override suspend fun downloadPart(ref: String, index: Int): String? = partsCollection().document(partId(ref, index))
        .get(Source.SERVER).awaitCompletion()
        .getString(FIELD_DATA)

    private fun mediaCollection() = firestore.collection(USERS)
        .document(requireUid())
        .collection(MEDIA)

    private fun partsCollection() = firestore.collection(USERS)
        .document(requireUid())
        .collection(MEDIA_PARTS)

    private fun partId(ref: String, index: Int): String = "${ref}_$index"

    // Sin sesión no hay a dónde sincronizar; excepción neutra (regla 4).
    private fun requireUid(): String = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()

    companion object {
        // Nombres neutros (RNF-05), como `entries` y `meta`.
        private const val USERS = "users"
        const val MEDIA = "media"
        const val MEDIA_PARTS = "mediaparts"
        private const val FIELD_DATA = "d"
        private const val FIELD_META = "payload"
        private const val FIELD_IV = "iv"
        private const val FIELD_PART_COUNT = "n"
        private const val FIELD_SCHEMA_VERSION = "schemaVersion"

        /**
         * Techo de trozos por adjunto para el borrado a ciegas: el archivo más
         * grande permitido (10 MB + sobrecosto GCM) partido en trozos de
         * 640 KiB cabe en 17 documentos. Borrar de más es un no-op.
         */
        const val MAX_BLIND_PARTS = 17
    }
}
