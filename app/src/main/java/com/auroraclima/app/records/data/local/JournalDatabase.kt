package com.auroraclima.app.records.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * BD del historial personal, SIEMPRE construida por `EncryptedDatabaseFactory`
 * (Room + SQLCipher, RNF-01): este esquema nunca debe abrirse sin cifrar.
 * El nombre del archivo es neutro (RNF-05), como `store_meta`/`search_index`.
 */
@Database(entities = [JournalEntryRow::class], version = 1, exportSchema = false)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val NAME = "journal_store.db"
    }
}
