package com.auroraclima.app.questionnaire.data.local

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
 * v1 → v2 del almacén de resultados guardados (ADR 2026-10-06): cada resultado
 * adopta su id numérico como nombre de documento en la nube —el mismo con el
 * que ya está publicado, así que no se migra nada de lo subido— y aparece la
 * tabla de constancias de borrado.
 *
 * Es la primera migración de esta base y la que faltaba cubrir: si el esquema
 * migrado no coincidiera con el que Room espera, la base NO abriría y el
 * listado de RF-31 se vería vacío sin explicación (el fallo se traduce a
 * `Result` y muere en silencio). Sobre SQLite del framework por la razón de
 * siempre: SQLCipher no carga en la JVM del host y el DDL es idéntico.
 */
@RunWith(RobolectricTestRunner::class)
class GuidanceDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun openV1WithData(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // en memoria
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Esquema v1 tal como quedó con RF-31 (ADR 2026-08-14).
                        db.execSQL(
                            "CREATE TABLE `outcomes` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`saved_at` INTEGER NOT NULL, `rank` INTEGER NOT NULL, " +
                                "`label` TEXT NOT NULL, `tags` TEXT NOT NULL, " +
                                "`synced_gen` INTEGER NOT NULL DEFAULT 0)"
                        )
                        db.execSQL(
                            "INSERT INTO outcomes (saved_at, rank, label, tags, synced_gen) " +
                                "VALUES (1750000000000, 3, 'Nivel guardado', '[\"a\"]', 4)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        return helper.writableDatabase
    }

    @Test
    fun `v1 a v2 da a cada resultado su nombre en la nube sin migrar lo ya publicado`() {
        val db = openV1WithData()

        GuidanceDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT id, doc_id, cloud_seen, label, synced_gen FROM outcomes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            val id = cursor.getLong(0)
            // El nombre remoto de lo ya publicado ES su id numérico.
            assertEquals(id.toString(), cursor.getString(1))
            // Nadie lo ha visto todavía en el servidor: no puede borrarse aún.
            assertEquals(0, cursor.getInt(2))
            // Y el contenido guardado sobrevive intacto.
            assertEquals("Nivel guardado", cursor.getString(3))
            assertEquals(4L, cursor.getLong(4))
        }
    }

    @Test
    fun `v1 a v2 crea la tabla de constancias de borrado`() {
        val db = openV1WithData()

        GuidanceDatabase.MIGRATION_1_2.migrate(db)

        db.execSQL("INSERT INTO removed (doc_id, at) VALUES ('abc', 7)")
        db.query("SELECT doc_id, at FROM removed").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("abc", cursor.getString(0))
            assertEquals(7L, cursor.getLong(1))
        }
    }
}
