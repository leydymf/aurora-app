package com.auroraclima.app.records.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface JournalDao {
    @Insert
    suspend fun insert(row: JournalEntryRow): Long

    /** Cronológico inverso (RF-34 criterio 1); a igual día, el creado más tarde primero. */
    @Query("SELECT * FROM entries ORDER BY day DESC, created_at DESC, id DESC")
    suspend fun entries(): List<JournalEntryRow>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entryById(id: Long): JournalEntryRow?

    /** Resuelve el registro por su nombre en la nube (reconciliación, ADR 2026-10-06). */
    @Query("SELECT * FROM entries WHERE doc_id = :docId")
    suspend fun entryByDocId(docId: String): JournalEntryRow?

    /**
     * Edita el contenido (RF-35) sin tocar `created_at`: el orden del listado no
     * cambia. Marca la fila como pendiente (RF-39): la copia subida quedó
     * desfasada y si la re-subida inmediata fallara, el reconciliador la
     * retoma. `synced_gen` NO se borra: sigue diciendo que esta fila llegó a
     * estar en la nube, que es lo que decide el conflicto con una eliminación
     * hecha en otro dispositivo (ADR 2026-10-06). `updated_at` desempata.
     */
    @Query(
        "UPDATE entries SET day = :day, kind = :kind, label = :label, detail = :detail, " +
            "updated_at = :updatedAt, pending = 1 WHERE id = :id"
    )
    suspend fun update(id: Long, day: Long, kind: String, label: String, detail: String, updatedAt: Long)

    /**
     * Adopta la versión de otro dispositivo (ADR 2026-10-06). Conserva el id
     * local y el `doc_id` —son los mismos— y deja la constancia puesta: lo que
     * se acaba de bajar YA está en la nube con esta DEK, así que no se re-sube.
     */
    @Query(
        "UPDATE entries SET day = :day, kind = :kind, label = :label, detail = :detail, " +
            "updated_at = :updatedAt, synced_gen = :generation, pending = 0 WHERE id = :id"
    )
    suspend fun adopt(
        id: Long,
        day: Long,
        kind: String,
        label: String,
        detail: String,
        updatedAt: Long,
        generation: Long
    )

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Lo que hay que subir (RF-39): lo que nunca subió o subió con otra
     * generación de la DEK, y lo que se editó aquí desde la última subida.
     */
    @Query("SELECT * FROM entries WHERE synced_gen != :generation OR pending != 0")
    suspend fun pendingSync(generation: Long): List<JournalEntryRow>

    /** La restauración (RF-39) solo repone sobre un historial vacío. */
    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Long

    /**
     * Marca en qué generación de la DEK vio este documento una lectura del
     * SERVIDOR. Es lo único que autoriza a borrarlo cuando deje de estar
     * (auditoría 2026-10-06): la subida solo encola, así que no prueba que el
     * servidor lo tenga, y la generación evita que una marca de antes de un
     * reinicio de respaldo autorice a borrar lo que la purga se llevó.
     */
    @Query("UPDATE entries SET cloud_seen = :generation WHERE doc_id = :docId")
    suspend fun markCloudSeen(docId: String, generation: Long)

    /**
     * Deja constancia de con qué generación subió el registro y lo da por
     * enviado (RF-39). La guarda `updated_at = :sealedAt` es lo que evita
     * perder una edición hecha ENTRE la lectura de la fila y esta marca: si la
     * fila cambió mientras tanto, sigue pendiente y el reconciliador la retoma
     * (auditoría 2026-10-06).
     */
    @Query("UPDATE entries SET synced_gen = :generation, pending = 0 WHERE id = :id AND updated_at = :sealedAt")
    suspend fun markSynced(id: Long, generation: Long, sealedAt: Long)
}
