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
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import kotlinx.coroutines.runBlocking
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
    private val journalDatabaseName = "probe_journal_store.db"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(databaseName)
        context.deleteDatabase(journalDatabaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
        context.deleteDatabase(journalDatabaseName)
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
    fun journalDataIsNotReadableInDatabaseFile() {
        // RF-32 criterio 1 / RNF-01 sobre el esquema REAL del historial: ni la
        // cabecera ni el texto guardado pueden leerse en los archivos en disco
        // (incluido el WAL, que también cifra SQLCipher).
        val sentinel = "centinela-legible-en-claro-jamas"
        val database = newFactory().create(JournalDatabase::class.java, journalDatabaseName)
        runBlocking {
            database.journalDao().insert(
                JournalEntryRow(day = 20_000, kind = "t01", label = sentinel, detail = sentinel, createdAt = 1L)
            )
        }
        database.close()

        val files = listOf(
            context.getDatabasePath(journalDatabaseName),
            context.getDatabasePath("$journalDatabaseName-wal")
        ).filter { it.exists() }
        assertTrue(files.isNotEmpty())
        val sentinelBytes = sentinel.toByteArray(Charsets.UTF_8)
        files.forEach { file ->
            val bytes = file.readBytes()
            assertFalse(bytes.copyOf(PLAIN_HEADER.size).contentEquals(PLAIN_HEADER))
            assertFalse("${file.name} contiene el texto en claro", bytes.containsSlice(sentinelBytes))
        }
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

private fun ByteArray.containsSlice(slice: ByteArray): Boolean {
    if (slice.isEmpty() || size < slice.size) return false
    outer@ for (start in 0..size - slice.size) {
        for (offset in slice.indices) {
            if (this[start + offset] != slice[offset]) continue@outer
        }
        return true
    }
    return false
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
