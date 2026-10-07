package com.auroraclima.app.questionnaire.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutcomeDao {
    @Insert
    suspend fun insert(row: OutcomeRow): Long

    /** Del más reciente al más antiguo; a igual fecha, el guardado más tarde primero. */
    @Query("SELECT * FROM outcomes ORDER BY saved_at DESC, id DESC")
    suspend fun outcomes(): List<OutcomeRow>

    @Query("SELECT * FROM outcomes WHERE id = :id")
    suspend fun outcomeById(id: Long): OutcomeRow?

    /** Resuelve por el nombre del documento en la nube (reconciliación). */
    @Query("SELECT * FROM outcomes WHERE doc_id = :docId")
    suspend fun outcomeByDocId(docId: String): OutcomeRow?

    /** Guardados que una lectura del servidor vio EN ESTA generación (reconciliación). */
    @Query("SELECT * FROM outcomes WHERE cloud_seen = :generation")
    suspend fun seenInCloud(generation: Long): List<OutcomeRow>

    /** Marca en qué generación lo vio una lectura del servidor (auditoría 2026-10-06). */
    @Query("UPDATE outcomes SET cloud_seen = :generation WHERE doc_id = :docId")
    suspend fun markCloudSeen(docId: String, generation: Long)

    @Query("DELETE FROM outcomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Guardados cuya copia remota no corresponde a la generación vigente (RF-39). */
    @Query("SELECT * FROM outcomes WHERE synced_gen != :generation")
    suspend fun pendingSync(generation: Long): List<OutcomeRow>

    /** La restauración (RF-39) solo repone sobre un almacén vacío. */
    @Query("SELECT COUNT(*) FROM outcomes")
    suspend fun count(): Long

    /** Deja constancia de con qué generación subió el guardado (RF-39). */
    @Query("UPDATE outcomes SET synced_gen = :generation WHERE id = :id")
    suspend fun markSynced(id: Long, generation: Long)
}
