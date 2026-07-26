package com.auroraclima.app.records.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v2 → v3 (RF-39): la migración agrega `synced_gen` con DEFAULT 0 conservando el
 * historial — el 0 ES la marca de «pendiente de subir con el sobre nuevo», así
 * que el propio DDL deja los datos existentes listos para la migración remota.
 * Sobre SQLite del framework (sin cifrar) por la razón de siempre: SQLCipher no
 * carga en la JVM del host; el DDL migrado es idéntico en ambos motores.
 */
@RunWith(RobolectricTestRunner::class)
class JournalDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun openV2WithData(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // en memoria
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Esquema v2 tal como quedó tras RF-33.
                        db.execSQL(
                            "CREATE TABLE `entries` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`day` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                                "`detail` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO entries (day, kind, detail, created_at) " +
                                "VALUES (20632, 'economic', 'registro previo', 99)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        return helper.writableDatabase
    }

    @Test
    fun `agrega la columna con DEFAULT 0 y conserva el historial`() {
        val db = openV2WithData()

        JournalDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT day, kind, detail, created_at, synced_gen FROM entries").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(20632L, cursor.getLong(0))
            assertEquals("economic", cursor.getString(1))
            assertEquals("registro previo", cursor.getString(2))
            assertEquals(99L, cursor.getLong(3))
            // El registro previo queda PENDIENTE: la re-subida lo migrará.
            assertEquals(0L, cursor.getLong(4))
        }
    }

    @Test
    fun `las filas nuevas tras migrar tambien nacen pendientes`() {
        val db = openV2WithData()
        JournalDatabase.MIGRATION_2_3.migrate(db)

        db.execSQL(
            "INSERT INTO entries (day, kind, detail, created_at) VALUES (20633, 'physical', 'nuevo', 100)"
        )

        db.query("SELECT synced_gen FROM entries WHERE day = 20633").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0L, cursor.getLong(0))
        }
    }
}
