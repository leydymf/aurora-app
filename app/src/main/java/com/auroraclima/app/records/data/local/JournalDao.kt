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

    /**
     * Edita el contenido (RF-35) sin tocar `created_at`: el orden del listado no
     * cambia. `synced_gen` vuelve a 0 (RF-39): la copia subida quedó desfasada y
     * si la re-subida inmediata fallara, el reconciliador la retoma.
     */
    @Query(
        "UPDATE entries SET day = :day, kind = :kind, label = :label, detail = :detail, synced_gen = 0 WHERE id = :id"
    )
    suspend fun update(id: Long, day: Long, kind: String, label: String, detail: String)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Registros cuya copia remota no corresponde a la generación vigente (RF-39). */
    @Query("SELECT * FROM entries WHERE synced_gen != :generation")
    suspend fun pendingSync(generation: Long): List<JournalEntryRow>

    /** La restauración (RF-39) solo repone sobre un historial vacío. */
    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Long

    /** Deja constancia de con qué generación subió el registro (RF-39). */
    @Query("UPDATE entries SET synced_gen = :generation WHERE id = :id")
    suspend fun markSynced(id: Long, generation: Long)
}
