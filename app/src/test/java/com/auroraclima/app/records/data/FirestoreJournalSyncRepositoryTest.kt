package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RF-37 + RF-39: la sync lee la fila local, la sella con la DEK recuperable y
 * sube SOLO blobs (payload v2, con `at` para que la restauración conserve el
 * orden); el éxito deja constancia de la generación en la fila. Bajo Robolectric
 * por `org.json` y Room en memoria (SQLCipher no carga en la JVM del host).
 */
@RunWith(RobolectricTestRunner::class)
class FirestoreJournalSyncRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
    private val storeAccess = JournalStoreAccess { database }
    private val remote = FakeJournalSyncRemoteSource()
    private val cipher = ReversiblePayloadCipher()
    private val keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = 4)
    private val repository = FirestoreJournalSyncRepository(remote, cipher, storeAccess, keyManager)

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedRow(
        detail: String = "tomó el dinero del mercado",
        createdAt: Long = 1_750_000_000_000
    ): Long = database.journalDao().insert(
        JournalEntryRow(day = 20_632, kind = "economic", detail = detail, createdAt = createdAt)
    )

    @Test
    fun `el payload nunca viaja en claro y el docId es el id local`() = runTest {
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.uploaded.size)
        val (docId, dto) = remote.uploaded[0]
        assertEquals(id.toString(), docId)

        // El texto sensible no aparece en NINGÚN campo del DTO.
        val cleartext = "tomó el dinero del mercado"
        assertFalse(dto.ciphertext.contains(cleartext))
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains(cleartext))
        assertEquals(2L, dto.schemaVersion)

        // Round-trip: el payload v2 conserva día, tipo, relato y created_at (RF-39).
        val json = JSONObject(openPayload(dto.ciphertext, dto.iv))
        assertEquals(20_632L, json.getLong("day"))
        assertEquals("economic", json.getString("kind"))
        assertEquals(cleartext, json.getString("detail"))
        assertEquals(1_750_000_000_000L, json.getLong("at"))
    }

    @Test
    fun `el exito deja constancia de la generacion en la fila`() = runTest {
        val id = seedRow()

        repository.push(id)

        assertEquals(4L, database.journalDao().entryById(id)?.syncedGeneration)
    }

    @Test
    fun `sin DEK establecida el push falla y la fila queda pendiente`() = runTest {
        // Sesión sobreviviente a muerte de proceso sin sobre: el reconciliador
        // del siguiente login retoma la subida (RF-39).
        keyManager.generation = null
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isFailure)
        assertTrue(remote.uploaded.isEmpty())
        assertEquals(0L, database.journalDao().entryById(id)?.syncedGeneration)
    }

    @Test
    fun `sin conexión encola, sube al reconectar y la constancia queda`() = runTest {
        // La cola durable del SDK es la garantía de entrega (RF-38): encolar
        // cuenta como entregado y la constancia se escribe de una vez.
        remote.online = false
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertTrue(remote.uploaded.isEmpty())
        assertEquals(1, remote.queued.size)
        assertEquals(4L, database.journalDao().entryById(id)?.syncedGeneration)

        remote.reconnect()
        assertEquals(id.toString(), remote.uploaded[0].first)
    }

    @Test
    fun `la edicion re-cifra y sobreescribe el mismo documento`() = runTest {
        // RF-35 criterio 1: mismo docId, payload nuevo.
        val id = seedRow()
        repository.push(id)
        database.journalDao().update(id, day = 20_633, kind = "economic", detail = "el relato corregido")

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertEquals(2, remote.uploaded.size)
        assertEquals(remote.uploaded[0].first, remote.uploaded[1].first)
        val dto = remote.uploaded[1].second
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains("corregido"))
        assertEquals("el relato corregido", JSONObject(openPayload(dto.ciphertext, dto.iv)).getString("detail"))
    }

    @Test
    fun `un registro inexistente falla sin subir nada`() = runTest {
        assertTrue(repository.push(99).isFailure)
        assertTrue(remote.uploaded.isEmpty())
    }

    @Test
    fun `remove borra el documento del id local`() = runTest {
        val result = repository.remove(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf("7"), remote.deleted)
    }

    @Test
    fun `sin conexion el borrado se encola y al reconectar se propaga`() = runTest {
        remote.online = false

        val result = repository.remove(7)

        assertTrue(result.isSuccess)
        assertTrue(remote.deleted.isEmpty())
        assertEquals(listOf("7"), remote.queuedDeletes)

        remote.reconnect()
        assertEquals(listOf("7"), remote.deleted)
    }

    private fun openPayload(ciphertext: String, iv: String): String = cipher.open(
        SealedPayload(
            ciphertext = Base64.getDecoder().decode(ciphertext),
            iv = Base64.getDecoder().decode(iv)
        )
    ).toString(Charsets.UTF_8)
}
