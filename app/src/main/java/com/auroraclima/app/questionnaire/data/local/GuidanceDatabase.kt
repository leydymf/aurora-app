package com.auroraclima.app.questionnaire.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Almacén cifrado propio del cuestionario (RF-31; ADR 2026-08-14), SIEMPRE
 * construido por `EncryptedDatabaseFactory` (Room + SQLCipher, RNF-01) con la
 * misma passphrase de `DatabaseKeyManager` que el historial: el cripto-borrado
 * de `DatabaseKeyEraser` (RF-20) lo cubre por construcción. BD separada de
 * `journal_store.db` porque las features no se importan entre sí (§2) y
 * `core/database` solo aporta configuración (ADR 2026-07-07). Nombre de archivo
 * neutro, como el resto (RNF-05).
 *
 * v2 (ADR 2026-10-06): `outcomes` gana `doc_id` —su nombre en la nube, propio
 * e independiente del id local— y aparece la tabla `removed` de constancias de
 * borrado, para que la reconciliación con la nube no resucite lo eliminado.
 */
@Database(entities = [OutcomeRow::class, RemovedOutcomeRow::class], version = 2, exportSchema = false)
abstract class GuidanceDatabase : RoomDatabase() {
    abstract fun outcomeDao(): OutcomeDao

    abstract fun removedOutcomeDao(): RemovedOutcomeDao

    companion object {
        const val NAME = "guidance_store.db"

        /**
         * v1 → v2 (ADR 2026-10-06). Lo ya publicado NO se migra: cada resultado
         * existente adopta su id numérico como nombre de documento, que es con
         * el que ya está en la nube. Solo los guardados nuevos reciben un id
         * opaco.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `outcomes` ADD COLUMN `doc_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `outcomes` SET `doc_id` = CAST(`id` AS TEXT) WHERE `doc_id` = ''")
                db.execSQL("ALTER TABLE `outcomes` ADD COLUMN `cloud_seen` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `removed` (" +
                        "`doc_id` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`doc_id`))"
                )
            }
        }
    }
}
