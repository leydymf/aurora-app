package com.auroraclima.app.questionnaire.data.remote

import com.auroraclima.app.core.network.awaitCompletion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Único conocedor del SDK de Firestore en el feature (RF-31). Colección PLANA
 * `/users/{uid}/outcomes` con nombre neutro (RNF-05), restringida a la cuenta
 * por el uid y `firestore.rules`. `set`/`delete` dejan la mutación en la cola
 * durable del SDK sin esperar al servidor (RF-38); la lectura de restauración
 * y la purga de RF-20 sí esperan (`Source.SERVER`).
 */
@Singleton
class FirestoreOutcomeRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : OutcomeSyncRemoteSource {
    override suspend fun upload(documentId: String, document: EncryptedOutcomeDto) {
        outcomes().document(documentId).set(
            mapOf(
                FIELD_PAYLOAD to document.ciphertext,
                FIELD_IV to document.iv,
                FIELD_SCHEMA_VERSION to document.schemaVersion
            )
        )
    }

    override suspend fun delete(documentId: String) {
        outcomes().document(documentId).delete()
    }

    /** Restauración (RF-39): aquí SÍ se espera al servidor — es una lectura. */
    override suspend fun downloadAll(): List<Pair<String, EncryptedOutcomeDto>> =
        outcomes().get(Source.SERVER).awaitCompletion().documents.mapNotNull { document ->
            val dto = EncryptedOutcomeDto(
                ciphertext = document.getString(FIELD_PAYLOAD) ?: return@mapNotNull null,
                iv = document.getString(FIELD_IV) ?: return@mapNotNull null,
                schemaVersion = document.getLong(FIELD_SCHEMA_VERSION) ?: return@mapNotNull null
            )
            document.id to dto
        }

    /** `/users/{uid}/outcomes` de la usuaria autenticada. */
    private fun outcomes() = firestore.collection(USERS)
        .document(requireUid())
        .collection(OUTCOMES)

    // Sin sesión no hay a dónde sincronizar; excepción neutra y sin mensaje (regla 4).
    private fun requireUid(): String = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()

    internal companion object {
        // Nombres neutros: viajan en las rutas del SDK y quedan en la caché local (RNF-05).
        const val USERS = "users"
        const val OUTCOMES = "outcomes"
        const val FIELD_PAYLOAD = "payload"
        const val FIELD_IV = "iv"
        const val FIELD_SCHEMA_VERSION = "schemaVersion"
    }
}

/**
 * Purga de `/users/{uid}/outcomes` esperando al servidor (RF-20 criterio 2).
 * Enumera DESDE EL SERVIDOR (cubre huérfanos de instalaciones previas con el
 * mismo uid); sin red falla — el borrado debe ser completo o no iniciarse.
 * Idempotente: reintentar resuelve un fallo a mitad.
 */
@Singleton
class FirestoreOutcomePurgeRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : OutcomePurgeRemoteSource {
    override suspend fun deleteAll() {
        val uid = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()
        val collection = firestore.collection(FirestoreOutcomeRemoteSource.USERS)
            .document(uid)
            .collection(FirestoreOutcomeRemoteSource.OUTCOMES)
        val snapshot = collection.get(Source.SERVER).awaitCompletion()
        snapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { document -> batch.delete(document.reference) }
            batch.commit().awaitCompletion()
        }
    }

    private companion object {
        /** Límite documentado de operaciones por WriteBatch de Firestore. */
        const val BATCH_LIMIT = 500
    }
}
