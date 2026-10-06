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
 * v6 (ADR 2026-10-06, reconciliación con la nube): `entries` gana `doc_id` (su
 * nombre en la nube, independiente del id local) y `updated_at` (la edición que
 * desempata entre dispositivos), y aparece la tabla `removed` de constancias de
 * borrado. Todas las migraciones conservan lo ya guardado.
 */
@Database(
    entities = [JournalEntryRow::class, AttachmentRow::class, RemovedRow::class],
    version = 6,
    exportSchema = false
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun removedDao(): RemovedDao

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

        /**
         * v5 → v6 (ADR 2026-10-06): separa el nombre del documento en la nube
         * del id local y agrega la fecha de edición y las constancias de
         * borrado.
         *
         * Lo ya publicado NO se migra: cada registro existente adopta su propio
         * id numérico como `doc_id`, que es exactamente el nombre con el que ya
         * está subido. Solo los registros creados a partir de aquí reciben un
         * id opaco nuevo, así que ningún documento de la nube cambia de nombre
         * ni hay que re-subir nada por este motivo.
         *
         * `cloud_seen` arranca en 0 para TODO, también para lo que ya está
         * subido: la primera reconciliación lo verá en el servidor y lo
         * marcará. Empezar en 0 solo retrasa un ciclo el borrado de algo que
         * se eliminó en otro dispositivo, y a cambio hace imposible borrar
         * algo que el servidor nunca confirmó.
         *
         * `pending` separa «tiene cambios sin subir» de «con qué generación
         * subió»: hasta v5 una edición ponía `synced_gen` en 0 y entonces un
         * registro editado aquí era indistinguible de uno que nunca subió.
         *
         * `updated_at` arranca en `created_at`: un registro que nunca se editó
         * se modificó por última vez cuando se creó, y así la comparación con
         * un payload antiguo (que no trae la fecha de edición y usa su fecha de
         * creación) no produce empates falsos.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `doc_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `pending` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `cloud_seen` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `cloud_seen` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `entries` SET `doc_id` = CAST(`id` AS TEXT) WHERE `doc_id` = ''")
                db.execSQL("UPDATE `entries` SET `updated_at` = `created_at` WHERE `updated_at` = 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `removed` (" +
                        "`doc_id` TEXT NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`doc_id`, `scope`))"
                )
            }
        }
    }
}
