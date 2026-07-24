package com.auroraclima.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Fábrica única de bases de datos Room cifradas con SQLCipher (RNF-01, P-03).
 *
 * `core/database` solo aporta la configuración (ADR 2026-07-07): las entidades
 * y DAOs los declara cada feature — `records/` construirá aquí su BD (día 6)
 * sin conocer la passphrase ni el Keystore. La BD de clima NO pasa por aquí:
 * es del feature `weather` y va sin cifrar a propósito (mismo ADR).
 */
@Singleton
class EncryptedDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: DatabaseKeyManager
) {
    fun <T : RoomDatabase> create(databaseClass: Class<T>, fileName: String, vararg migrations: Migration): T {
        // Idempotente: el runtime ignora cargas repetidas de la misma librería.
        System.loadLibrary(SQLCIPHER_LIBRARY)
        return Room.databaseBuilder(context, databaseClass, fileName)
            .openHelperFactory(SupportOpenHelperFactory(keyManager.obtainPassphrase()))
            // Las migraciones las declara el dueño del esquema (records/); sin una
            // ruta de migración Room lanzaría y el historial ya guardado se
            // perdería — inaceptable para RF-32.
            .addMigrations(*migrations)
            .build()
    }

    companion object {
        private const val SQLCIPHER_LIBRARY = "sqlcipher"
    }
}
