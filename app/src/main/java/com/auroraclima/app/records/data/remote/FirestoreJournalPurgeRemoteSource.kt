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
 * así que un fallo a mitad se resuelve reintentando. Hoy `entries` es la única
 * subcolección bajo el uid; una colección nueva deberá sumarse aquí.
 */
@Singleton
class FirestoreJournalPurgeRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : JournalPurgeRemoteSource {
    override suspend fun deleteAll() {
        // Sin sesión no hay qué purgar; excepción neutra y sin mensaje (regla 4).
        val uid = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()
        val entries = firestore.collection(USERS).document(uid).collection(ENTRIES)
        val snapshot = entries.get(Source.SERVER).awaitCompletion()
        snapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { document -> batch.delete(document.reference) }
            batch.commit().awaitCompletion()
        }
    }

    private companion object {
        // Mismos nombres neutros que la sync (RNF-05).
        const val USERS = "users"
        const val ENTRIES = "entries"

        /** Límite documentado de operaciones por WriteBatch de Firestore. */
        const val BATCH_LIMIT = 500
    }
}
