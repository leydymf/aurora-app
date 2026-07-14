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
}
