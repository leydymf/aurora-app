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
