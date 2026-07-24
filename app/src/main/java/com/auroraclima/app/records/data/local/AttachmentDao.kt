package com.auroraclima.app.records.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(row: AttachmentRow): Long

    /** Adjuntos de un registro (o en preparación con owner = 0), en orden de llegada. */
    @Query("SELECT * FROM attachments WHERE owner = :owner ORDER BY id")
    suspend fun byOwner(owner: Long): List<AttachmentRow>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: Long): AttachmentRow?

    /** Contadores del listado (P18); los adjuntos en preparación no cuentan. */
    @Query("SELECT owner, COUNT(*) AS total FROM attachments WHERE owner != 0 GROUP BY owner")
    suspend fun countsByOwner(): List<OwnerCount>

    /** El guardado de un registro nuevo reclama sus adjuntos en preparación (RF-32/RF-33). */
    @Query("UPDATE attachments SET owner = :owner WHERE id IN (:ids)")
    suspend fun claim(ids: List<Long>, owner: Long)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Huérfanos: adjuntos cuyo dueño ya no existe — en preparación abandonados
     * (owner = 0) o restos de un borrado interrumpido. El barrido los limpia
     * junto con sus archivos (RF-36, autosaneamiento).
     */
    @Query("SELECT * FROM attachments WHERE owner NOT IN (SELECT id FROM entries)")
    suspend fun orphans(): List<AttachmentRow>
}

/** Proyección del contador por registro para el listado (P18). */
data class OwnerCount(val owner: Long, val total: Int)
