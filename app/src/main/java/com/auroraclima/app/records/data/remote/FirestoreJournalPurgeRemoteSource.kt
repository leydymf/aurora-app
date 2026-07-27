package com.auroraclima.app.records.data.remote

import com.auroraclima.app.core.network.awaitCompletion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Purga de `/users/{uid}/entries` esperando al servidor (RF-20 criterio 2).
 *
 * Enumera los documentos DESDE EL SERVIDOR (no desde la BD local ni la caché):
 * cubre blobs huérfanos de una instalación anterior con el mismo uid, cuyos ids
 * ya no existen localmente. Sin red, la consulta falla — deliberado: el borrado
 * debe ser completo o no iniciarse. Idempotente: borrar lo ya borrado es no-op,
 * así que un fallo a mitad se resuelve reintentando. Purga `entries` y, desde
 * RF-39 con evidencias, también `media` y `mediaparts` (por eso son
 * colecciones PLANAS: enumerables sin consultas de grupo).
 */
@Singleton
class FirestoreJournalPurgeRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : JournalPurgeRemoteSource {
    override suspend fun deleteAll() {
        // Sin sesión no hay qué purgar; excepción neutra y sin mensaje (regla 4).
        val uid = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()
        COLLECTIONS.forEach { name ->
            val collection = firestore.collection(USERS).document(uid).collection(name)
            val snapshot = collection.get(Source.SERVER).awaitCompletion()
            snapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { document -> batch.delete(document.reference) }
                batch.commit().awaitCompletion()
            }
        }
    }

    private companion object {
        // Mismos nombres neutros que la sync (RNF-05).
        const val USERS = "users"
        val COLLECTIONS = listOf(
            "entries",
            FirestoreJournalMediaRemoteSource.MEDIA,
            FirestoreJournalMediaRemoteSource.MEDIA_PARTS
        )

        /** Límite documentado de operaciones por WriteBatch de Firestore. */
        const val BATCH_LIMIT = 500
    }
}
