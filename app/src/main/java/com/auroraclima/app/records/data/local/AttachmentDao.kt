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

    /** Resuelve por el nombre opaco con el que el adjunto viaja a la nube. */
    @Query("SELECT * FROM attachments WHERE ref = :ref")
    suspend fun byRef(ref: String): AttachmentRow?

    /** Adjuntos que una lectura del servidor vio EN ESTA generación (reconciliación). */
    @Query("SELECT * FROM attachments WHERE owner != 0 AND cloud_seen = :generation")
    suspend fun seenInCloud(generation: Long): List<AttachmentRow>

    /** Marca en qué generación lo vio una lectura del servidor (auditoría 2026-10-06). */
    @Query("UPDATE attachments SET cloud_seen = :generation WHERE ref = :ref")
    suspend fun markCloudSeen(ref: String, generation: Long)

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

    /**
     * Adjuntos reclamados cuya copia remota no corresponde a la generación
     * vigente (RF-39): los en preparación (owner = 0) jamás suben.
     */
    @Query("SELECT * FROM attachments WHERE owner != 0 AND synced_gen != :generation")
    suspend fun pendingSync(generation: Long): List<AttachmentRow>

    /** Deja constancia de con qué generación subió el adjunto (RF-39). */
    @Query("UPDATE attachments SET synced_gen = :generation WHERE id = :id")
    suspend fun markSynced(id: Long, generation: Long)

    /** La restauración de evidencias (RF-39) solo repone sobre un almacén vacío. */
    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun count(): Long
}

/** Proyección del contador por registro para el listado (P18). */
data class OwnerCount(val owner: Long, val total: Int)
