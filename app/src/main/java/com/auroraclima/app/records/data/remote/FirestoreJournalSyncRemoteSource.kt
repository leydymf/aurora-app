package com.auroraclima.app.records.data.remote

import com.auroraclima.app.core.network.awaitCompletion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escritura de la sync sobre Firestore (RF-37). Único conocedor del SDK en el
 * feature. El `uid` de la sesión restringe el acceso a la cuenta de la usuaria
 * (criterio 2, reforzado por `firestore.rules`); sin sesión activa lanza y el
 * caso de uso lo ignora (mejor esfuerzo, §3).
 *
 * No se espera la confirmación del servidor: `set`/`delete` dejan la mutación en
 * la cola local persistente del SDK de forma síncrona y esta sube sola al
 * reconectar (RF-38). Esperar el `Task` colgaría la corrutina sin red; la cola
 * durable ya garantiza que la escritura sobrevive incluso al cierre de la app.
 */
@Singleton
class FirestoreJournalSyncRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : JournalSyncRemoteSource {
    override suspend fun upload(documentId: String, document: EncryptedEntryDto) {
        entries().document(documentId).set(
            mapOf(
                FIELD_PAYLOAD to document.ciphertext,
                FIELD_IV to document.iv,
                FIELD_SCHEMA_VERSION to document.schemaVersion
            )
        )
    }

    override suspend fun delete(documentId: String) {
        entries().document(documentId).delete()
    }

    /** Restauración (RF-39): aquí SÍ se espera al servidor — es una lectura. */
    override suspend fun downloadAll(): List<Pair<String, EncryptedEntryDto>> =
        entries().get(Source.SERVER).awaitCompletion().documents.mapNotNull { document ->
            val dto = EncryptedEntryDto(
                ciphertext = document.getString(FIELD_PAYLOAD) ?: return@mapNotNull null,
                iv = document.getString(FIELD_IV) ?: return@mapNotNull null,
                schemaVersion = document.getLong(FIELD_SCHEMA_VERSION) ?: return@mapNotNull null
            )
            document.id to dto
        }

    /** `/users/{uid}/entries` de la usuaria autenticada. */
    private fun entries() = firestore.collection(USERS)
        .document(requireUid())
        .collection(ENTRIES)

    // Sin sesión no hay a dónde sincronizar. Excepción neutra y sin mensaje: se
    // traduce a Result.failure aguas arriba y jamás se registra (regla 4).
    private fun requireUid(): String = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()

    private companion object {
        // Nombres neutros: viajan en las rutas del SDK y quedan en la caché local (RNF-05).
        const val USERS = "users"
        const val ENTRIES = "entries"
        const val FIELD_PAYLOAD = "payload"
        const val FIELD_IV = "iv"
        const val FIELD_SCHEMA_VERSION = "schemaVersion"
    }
}
