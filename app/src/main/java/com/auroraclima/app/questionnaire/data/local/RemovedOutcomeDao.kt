package com.auroraclima.app.questionnaire.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Constancias de borrado local pendientes de que la nube las confirme (ADR 2026-10-06). */
@Dao
interface RemovedOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RemovedOutcomeRow)

    @Query("SELECT doc_id FROM removed")
    suspend fun docIds(): List<String>

    /** Constancias puestas hace rato: las recién escritas no se retiran todavía. */
    @Query("SELECT doc_id FROM removed WHERE at <= :before")
    suspend fun docIdsSettled(before: Long): List<String>

    @Query("DELETE FROM removed WHERE doc_id = :docId")
    suspend fun delete(docId: String)

    /** Poda de constancias viejas que la nube nunca confirmó. */
    @Query("DELETE FROM removed WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
