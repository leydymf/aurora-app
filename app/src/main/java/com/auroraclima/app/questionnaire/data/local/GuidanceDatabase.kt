package com.auroraclima.app.questionnaire.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Almacén cifrado propio del cuestionario (RF-31; ADR 2026-08-14), SIEMPRE
 * construido por `EncryptedDatabaseFactory` (Room + SQLCipher, RNF-01) con la
 * misma passphrase de `DatabaseKeyManager` que el historial: el cripto-borrado
 * de `DatabaseKeyEraser` (RF-20) lo cubre por construcción. BD separada de
 * `journal_store.db` porque las features no se importan entre sí (§2) y
 * `core/database` solo aporta configuración (ADR 2026-07-07). Nombre de archivo
 * neutro, como el resto (RNF-05).
 */
@Database(entities = [OutcomeRow::class], version = 1, exportSchema = false)
abstract class GuidanceDatabase : RoomDatabase() {
    abstract fun outcomeDao(): OutcomeDao

    companion object {
        const val NAME = "guidance_store.db"
    }
}
