package com.auroraclima.app.records.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.auroraclima.app.records.data.legacyKindToTopicId

/**
 * BD del historial personal, SIEMPRE construida por `EncryptedDatabaseFactory`
 * (Room + SQLCipher, RNF-01): este esquema nunca debe abrirse sin cifrar.
 * El nombre del archivo es neutro (RNF-05), como `store_meta`/`search_index`.
 *
 * v2 (RF-33): tabla `attachments` — el índice de adjuntos vive cifrado junto a
 * los registros. v3 (RF-39): columna `synced_gen` en `entries` — generación de
 * la DEK con la que cada registro subió; 0 marca «pendiente», así que el propio
 * DEFAULT de la migración deja TODO el historial existente listo para
 * re-subirse con el sobre nuevo. v4 (RF-39 con evidencias): la misma columna en
 * `attachments` — los adjuntos existentes quedan pendientes de subir troceados.
 * v5 (ADR 2026-10-03, cierre P-68): `kind` pasa del código del catálogo fijo al
 * id del tema publicado y `entries` gana `label` (último nombre conocido); las
 * filas traducidas vuelven a `synced_gen` 0 para re-subirse como payload v3.
 * Todas las migraciones conservan lo ya guardado.
 */
@Database(entities = [JournalEntryRow::class, AttachmentRow::class], version = 5, exportSchema = false)
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

        /** v3 → v4 (RF-39 con evidencias): la misma constancia, ahora en `attachments`. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `synced_gen` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 → v5 (cierre P-68): agrega `label` y traduce cada código del
         * catálogo fijo al id del tema publicado equivalente. Idempotente (un
         * código ya traducido no coincide con ningún WHERE) y dentro de la
         * transacción de Room: si se interrumpe, se reintenta completa. Las
         * filas traducidas quedan PENDIENTES (`synced_gen` 0): el reconciliador
         * re-sube su copia remota como payload v3 — misma constancia de v2→v3.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `label` TEXT NOT NULL DEFAULT ''")
                legacyKindToTopicId.forEach { (legacyCode, topicId) ->
                    db.execSQL(
                        "UPDATE `entries` SET `kind` = ?, `synced_gen` = 0 WHERE `kind` = ?",
                        arrayOf(topicId, legacyCode)
                    )
                }
            }
        }
    }
}
