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
 * v4 → v5 (P-68): traduce los códigos del catálogo fijo a ids de tema, agrega
 * `label` y deja las filas traducidas pendientes de re-subir como payload v3.
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

    // ---- v4 → v5 (cierre P-68): tipos por id de tema publicado ----

    private fun openV4WithLegacyKinds(): SupportSQLiteDatabase {
        val db = openV2WithData()
        JournalDatabase.MIGRATION_2_3.migrate(db)
        // Un registro ya subido con el sobre (constancia 3) y uno de cada código legado.
        db.execSQL("UPDATE entries SET synced_gen = 3")
        db.execSQL(
            "INSERT INTO entries (day, kind, detail, created_at, synced_gen) VALUES " +
                "(20633, 'psychological', 'p', 100, 3), (20634, 'physical', 'f', 101, 3), " +
                "(20635, 'sexual', 's', 102, 3), (20636, 'otro', 'desconocido', 103, 3)"
        )
        return db
    }

    @Test
    fun `v4 a v5 traduce los codigos legados a ids de tema y los deja pendientes de re-subir`() {
        val db = openV4WithLegacyKinds()

        JournalDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT day, kind, label, synced_gen, detail, created_at FROM entries ORDER BY day").use { cursor ->
            assertEquals(5, cursor.count)
            val rows = generateSequence { if (cursor.moveToNext()) cursor else null }
                .map {
                    listOf(
                        it.getLong(0),
                        it.getString(1),
                        it.getString(2),
                        it.getLong(3),
                        it.getString(4),
                        it.getLong(5)
                    )
                }
                .toList()
            assertEquals(
                listOf(
                    listOf(20632L, "t04", "", 0L, "registro previo", 99L),
                    listOf(20633L, "t02", "", 0L, "p", 100L),
                    listOf(20634L, "t01", "", 0L, "f", 101L),
                    listOf(20635L, "t03", "", 0L, "s", 102L),
                    // Un código desconocido no se toca ni pierde su constancia.
                    listOf(20636L, "otro", "", 3L, "desconocido", 103L)
                ),
                rows
            )
        }
    }

    @Test
    fun `v4 a v5 es idempotente al repetir las traducciones`() {
        // Interrupción a mitad y reintento: volver a correr los UPDATE no cambia nada.
        val db = openV4WithLegacyKinds()
        JournalDatabase.MIGRATION_4_5.migrate(db)
        db.execSQL("UPDATE entries SET synced_gen = 5 WHERE kind = 't04'")

        db.execSQL("UPDATE `entries` SET `kind` = 't04', `synced_gen` = 0 WHERE `kind` = 'economic'")

        db.query("SELECT kind, synced_gen FROM entries WHERE day = 20632").use { cursor ->
            cursor.moveToFirst()
            assertEquals("t04", cursor.getString(0))
            assertEquals(5L, cursor.getLong(1))
        }
    }

    @Test
    fun `tras v5 las filas nuevas nacen con label vacio por defecto`() {
        val db = openV4WithLegacyKinds()
        JournalDatabase.MIGRATION_4_5.migrate(db)

        db.execSQL("INSERT INTO entries (day, kind, detail, created_at) VALUES (20640, 't05', 'nuevo', 200)")

        db.query("SELECT label, synced_gen FROM entries WHERE day = 20640").use { cursor ->
            cursor.moveToFirst()
            assertEquals("", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
        }
    }

    @Test
    fun `v3 a v4 agrega la constancia a attachments conservando los adjuntos`() {
        // Esquema v3: entries ya migrada; attachments tal como quedó en RF-33.
        val db = openV2WithData()
        JournalDatabase.MIGRATION_2_3.migrate(db)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`owner` INTEGER NOT NULL, `ref` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO attachments (owner, ref, kind, created_at) VALUES (1, 'abc123', 'image/jpeg', 42)"
        )

        JournalDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT owner, ref, kind, created_at, synced_gen FROM attachments").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals("abc123", cursor.getString(1))
            assertEquals("image/jpeg", cursor.getString(2))
            assertEquals(42L, cursor.getLong(3))
            // El adjunto previo queda PENDIENTE: la re-subida troceada lo migrará.
            assertEquals(0L, cursor.getLong(4))
        }
    }
}
