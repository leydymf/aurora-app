package com.auroraclima.app.records.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Constancias de borrado local pendientes de que la nube las confirme (ADR 2026-10-06). */
@Dao
interface RemovedDao {
    /** Reemplaza: volver a borrar el mismo documento solo refresca la fecha. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RemovedRow)

    @Query("SELECT doc_id FROM removed WHERE scope = :scope")
    suspend fun docIds(scope: String): List<String>

    /** Constancias puestas hace rato: las recién escritas no se retiran todavía. */
    @Query("SELECT doc_id FROM removed WHERE scope = :scope AND at <= :before")
    suspend fun docIdsSettled(scope: String, before: Long): List<String>

    @Query("DELETE FROM removed WHERE doc_id = :docId AND scope = :scope")
    suspend fun delete(docId: String, scope: String)

    /**
     * Poda de constancias viejas: si tras meses la nube nunca confirmó el
     * borrado, el documento ya no existe o la cuenta cambió de generación, y la
     * tabla no debe crecer sin fin.
     */
    @Query("DELETE FROM removed WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
