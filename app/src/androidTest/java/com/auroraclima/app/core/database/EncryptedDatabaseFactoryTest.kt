package com.auroraclima.app.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de P-03 (RNF-01 criterio 1): la BD creada por la fábrica queda cifrada
 * en reposo — el archivo no empieza con la cabecera «SQLite format 3» de una BD
 * en claro — y la passphrase envuelta en el Keystore sobrevive entre instancias
 * (reabrir la BD no la recifra ni la pierde).
 *
 * Instrumentada y no Robolectric: SQLCipher solo publica binarios nativos para
 * ABIs de Android y su librería no carga en la JVM del host.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "probe_store.db"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
    }

    private fun newFactory() = EncryptedDatabaseFactory(context, DatabaseKeyManager(context))

    @Test
    fun encryptedFileIsNotPlainSqlite() {
        val database = newFactory().create(ProbeDatabase::class.java, databaseName)
        database.probeDao().insert(ProbeRow(id = 1, note = "abc"))
        database.close()

        val file = context.getDatabasePath(databaseName)
        assertTrue(file.exists())
        val header = ByteArray(PLAIN_HEADER.size)
        file.inputStream().use { it.read(header) }
        // Una BD sin cifrar empieza exactamente con esta cabecera.
        assertFalse(header.contentEquals(PLAIN_HEADER))
    }

    @Test
    fun passphraseSurvivesAcrossInstances() {
        val first = newFactory().create(ProbeDatabase::class.java, databaseName)
        first.probeDao().insert(ProbeRow(id = 1, note = "abc"))
        first.close()

        // Instancias nuevas de fábrica y gestor: la passphrase debe salir del
        // blob envuelto en el Keystore, no de memoria.
        val second = newFactory().create(ProbeDatabase::class.java, databaseName)
        assertEquals(1, second.probeDao().count())
        second.close()
    }

    companion object {
        // Cabecera de una BD SQLite en claro: 15 caracteres ASCII más un byte nulo.
        private val PLAIN_HEADER = "SQLite format 3".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
    }
}

/** BD mínima de prueba: core/database no define entidades de negocio (ADR 2026-07-07). */
@Entity(tableName = "probe")
internal data class ProbeRow(
    @PrimaryKey val id: Long,
    val note: String
)

@Dao
internal interface ProbeDao {
    @Insert
    fun insert(row: ProbeRow)

    @Query("SELECT COUNT(*) FROM probe")
    fun count(): Int
}

@Database(entities = [ProbeRow::class], version = 1, exportSchema = false)
internal abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}
