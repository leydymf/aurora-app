package com.auroraclima.app.records.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * BD del historial personal, SIEMPRE construida por `EncryptedDatabaseFactory`
 * (Room + SQLCipher, RNF-01): este esquema nunca debe abrirse sin cifrar.
 * El nombre del archivo es neutro (RNF-05), como `store_meta`/`search_index`.
 *
 * v2 (RF-33): tabla `attachments` — el índice de adjuntos vive cifrado junto a
 * los registros. v3 (RF-39): columna `synced_gen` — generación de la DEK con la
 * que cada registro subió; 0 marca «pendiente», así que el propio DEFAULT de la
 * migración deja TODO el historial existente listo para re-subirse con el sobre
 * nuevo. Ambas migraciones conservan lo ya guardado.
 */
@Database(entities = [JournalEntryRow::class, AttachmentRow::class], version = 3, exportSchema = false)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val NAME = "journal_store.db"

        /** v1 → v2: solo agrega la tabla de adjuntos; `entries` queda intacta. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`owner` INTEGER NOT NULL, " +
                        "`ref` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_owner` ON `attachments` (`owner`)")
            }
        }

        /**
         * v2 → v3 (RF-39): solo agrega `synced_gen` con DEFAULT 0 — todo lo ya
         * guardado queda pendiente de subir con la DEK recuperable; el
         * reconciliador del primer login tras actualizar hace la migración de
         * los documentos remotos re-subiendo desde aquí (la fuente de verdad).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `synced_gen` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
