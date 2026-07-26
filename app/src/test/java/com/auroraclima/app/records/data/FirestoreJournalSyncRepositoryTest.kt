package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.data.remote.EncryptedEntryDto
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

    // ---- Restauración multi-dispositivo (RF-39 criterio 1) ----

    private fun sealedDto(day: Long, detail: String, createdAt: Long, schemaVersion: Long = 2L): EncryptedEntryDto {
        val json = JSONObject()
            .put("day", day)
            .put("kind", "economic")
            .put("detail", detail)
            .put("at", createdAt)
        val sealed = cipher.seal(json.toString().toByteArray(Charsets.UTF_8))
        return EncryptedEntryDto(
            ciphertext = Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = Base64.getEncoder().encodeToString(sealed.iv),
            schemaVersion = schemaVersion
        )
    }

    @Test
    fun `sobre un historial vacio repone los registros con sus ids y su orden`() = runTest {
        // «Dispositivo B»: BD local vacía; en la nube hay dos registros v2.
        remote.remoteDocs += "3" to sealedDto(day = 20_632, detail = "el más reciente", createdAt = 200)
        remote.remoteDocs += "1" to sealedDto(day = 20_632, detail = "el más antiguo", createdAt = 100)

        val result = repository.restore()

        assertTrue(result.isSuccess)
        val rows = database.journalDao().entries()
        // Mismo día: el created_at restaurado del payload preserva el desempate (RF-34).
        assertEquals(listOf(3L, 1L), rows.map { it.id })
        assertEquals(listOf("el más reciente", "el más antiguo"), rows.map { it.detail })
        // Lo restaurado ya está en la nube: queda con constancia, no se re-sube.
        assertTrue(rows.all { it.syncedGeneration == 4L })
    }

    @Test
    fun `tras restaurar los registros nuevos no chocan con los ids repuestos`() = runTest {
        remote.remoteDocs += "5" to sealedDto(day = 20_632, detail = "restaurado", createdAt = 100)
        repository.restore()

        val newId = database.journalDao().insert(
            JournalEntryRow(day = 20_640, kind = "physical", detail = "nuevo", createdAt = 300)
        )

        // AUTOINCREMENT continúa por encima del máximo restaurado: el docId
        // determinista jamás sobreescribe un documento ajeno.
        assertTrue(newId > 5L)
    }

    @Test
    fun `con historial local no vacio no repone nada`() = runTest {
        // Mezclar podría resucitar registros con borrado aún en cola.
        val existing = seedRow()
        remote.remoteDocs += "9" to sealedDto(day = 20_632, detail = "remoto", createdAt = 100)

        val result = repository.restore()

        assertTrue(result.isSuccess)
        assertEquals(listOf(existing), database.journalDao().entries().map { it.id })
    }

    @Test
    fun `los blobs ilegibles y los formatos viejos se omiten sin romper la restauracion`() = runTest {
        // Un doc v1 (llave del dispositivo viejo) y un blob corrupto no deben
        // impedir reponer lo que sí abre.
        remote.remoteDocs += "1" to sealedDto(day = 20_632, detail = "legible", createdAt = 100)
        remote.remoteDocs += "2" to sealedDto(day = 20_632, detail = "v1", createdAt = 50, schemaVersion = 1L)
        remote.remoteDocs += "3" to EncryptedEntryDto(
            ciphertext = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
            iv = Base64.getEncoder().encodeToString(ByteArray(12)),
            schemaVersion = 2L
        )

        val result = repository.restore()

        assertTrue(result.isSuccess)
        assertEquals(listOf(1L), database.journalDao().entries().map { it.id })
    }

    @Test
    fun `sin DEK establecida la restauracion falla sin tocar la BD`() = runTest {
        keyManager.generation = null
        remote.remoteDocs += "1" to sealedDto(day = 20_632, detail = "x", createdAt = 100)

        assertTrue(repository.restore().isFailure)
        assertEquals(0L, database.journalDao().count())
    }

    private fun openPayload(ciphertext: String, iv: String): String = cipher.open(
        SealedPayload(
            ciphertext = Base64.getDecoder().decode(ciphertext),
            iv = Base64.getDecoder().decode(iv)
        )
    ).toString(Charsets.UTF_8)
}
