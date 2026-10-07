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
 * sube SOLO blobs (payload v3: `at` para que la restauración conserve el orden,
 * `kind` como id de tema y `label` como último nombre conocido — P-68); el
 * éxito deja constancia de la generación en la fila. La restauración sigue
 * leyendo los v2 de un dispositivo anterior. Bajo Robolectric por `org.json` y
 * Room en memoria (SQLCipher no carga en la JVM del host).
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

    private var nextDocId = 0

    /** Fila como la crea el repositorio real: con su nombre de documento propio. */
    private suspend fun seedRow(
        detail: String = "tomó el dinero del mercado",
        createdAt: Long = 1_750_000_000_000
    ): Long = database.journalDao().insert(
        JournalEntryRow(
            docId = "doc-${++nextDocId}",
            day = 20_632,
            kind = "t04",
            label = "Cuarta",
            detail = detail,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    )

    @Test
    fun `el payload nunca viaja en claro y el docId es el nombre propio del registro`() = runTest {
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.uploaded.size)
        val (docId, dto) = remote.uploaded[0]
        // El nombre del documento es el propio del registro, no su id local
        // (ADR 2026-10-06): dos dispositivos no pueden chocar.
        assertEquals(database.journalDao().entryById(id)!!.docId, docId)

        // El texto sensible no aparece en NINGÚN campo del DTO.
        val cleartext = "tomó el dinero del mercado"
        assertFalse(dto.ciphertext.contains(cleartext))
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains(cleartext))
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains("Cuarta"))
        assertEquals(4L, dto.schemaVersion)

        // Round-trip: el payload v3 conserva día, id del tema, su nombre, relato y created_at (RF-39, P-68).
        val json = JSONObject(openPayload(dto.ciphertext, dto.iv))
        assertEquals(20_632L, json.getLong("day"))
        assertEquals("t04", json.getString("kind"))
        assertEquals("Cuarta", json.getString("label"))
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
        assertEquals(database.journalDao().entryById(id)!!.docId, remote.uploaded[0].first)
    }

    @Test
    fun `la edicion re-cifra y sobreescribe el mismo documento`() = runTest {
        // RF-35 criterio 1: mismo docId, payload nuevo.
        val id = seedRow()
        repository.push(id)
        database.journalDao()
            .update(id, day = 20_633, kind = "t04", label = "Cuarta", detail = "el relato corregido", updatedAt = 99)

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
    fun `remove borra el documento por su nombre en la nube`() = runTest {
        val result = repository.remove("7")

        assertTrue(result.isSuccess)
        assertEquals(listOf("7"), remote.deleted)
    }

    @Test
    fun `sin conexion el borrado se encola y al reconectar se propaga`() = runTest {
        remote.online = false

        val result = repository.remove("7")

        assertTrue(result.isSuccess)
        assertTrue(remote.deleted.isEmpty())
        assertEquals(listOf("7"), remote.queuedDeletes)

        remote.reconnect()
        assertEquals(listOf("7"), remote.deleted)
    }

    // ---- Restauración multi-dispositivo (RF-39 criterio 1) ----

    private fun sealedDto(
        day: Long,
        detail: String,
        createdAt: Long,
        schemaVersion: Long = 3L,
        kind: String = "t04",
        label: String? = "Cuarta"
    ): EncryptedEntryDto {
        val json = JSONObject()
            .put("day", day)
            .put("kind", kind)
            .put("detail", detail)
            .put("at", createdAt)
        if (label != null) json.put("label", label)
        val sealed = cipher.seal(json.toString().toByteArray(Charsets.UTF_8))
        return EncryptedEntryDto(
            ciphertext = Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = Base64.getEncoder().encodeToString(sealed.iv),
            schemaVersion = schemaVersion
        )
    }

    @Test
    fun `sobre un historial vacio repone los registros con su nombre remoto y su orden`() = runTest {
        // «Dispositivo B»: BD local vacía; en la nube hay dos registros v3.
        remote.remoteDocs += "3" to sealedDto(day = 20_632, detail = "el más reciente", createdAt = 200)
        remote.remoteDocs += "1" to sealedDto(day = 20_632, detail = "el más antiguo", createdAt = 100)

        val result = repository.restore()

        assertTrue(result.isSuccess)
        val rows = database.journalDao().entries()
        // Mismo día: el created_at restaurado del payload preserva el desempate (RF-34).
        // El id local lo asigna la base; lo que se conserva es el nombre remoto.
        assertEquals(listOf("3", "1"), rows.map { it.docId })
        assertEquals(listOf("el más reciente", "el más antiguo"), rows.map { it.detail })
        // El tipo y su último nombre conocido se reponen tal cual (P-68).
        assertTrue(rows.all { it.kind == "t04" && it.label == "Cuarta" })
        // Lo restaurado ya está en la nube: queda con constancia, no se re-sube.
        assertTrue(rows.all { it.syncedGeneration == 4L })
    }

    @Test
    fun `un payload v2 de un dispositivo anterior se repone traduciendo el codigo legado`() = runTest {
        // Dispositivo A sin actualizar subió v2 con el catálogo fijo; B (esta
        // versión) debe leerlo: código legado → id de tema, sin instantánea.
        remote.remoteDocs += "1" to sealedDto(
            day = 20_632, detail = "viejo", createdAt = 100, schemaVersion = 2L, kind = "psychological", label = null
        )
        remote.remoteDocs += "2" to sealedDto(
            day = 20_632, detail = "raro", createdAt = 50, schemaVersion = 2L, kind = "otro", label = null
        )

        val result = repository.restore()

        assertTrue(result.isSuccess)
        val rows = database.journalDao().entries().associateBy { it.id }
        assertEquals("t02", rows.getValue(1L).kind)
        assertEquals("", rows.getValue(1L).label)
        // Un código que no está en el mapa se conserva: se mostrará neutro, no se pierde.
        assertEquals("otro", rows.getValue(2L).kind)
        assertTrue(rows.values.all { it.syncedGeneration == 4L })
    }

    @Test
    fun `un payload v3 sin label se repone con instantanea vacia`() = runTest {
        remote.remoteDocs += "1" to sealedDto(day = 20_632, detail = "x", createdAt = 100, label = null)

        repository.restore()

        assertEquals("", database.journalDao().entryById(1)?.label)
    }

    @Test
    fun `tras restaurar, un registro nuevo no reutiliza el nombre remoto de uno repuesto`() = runTest {
        remote.remoteDocs += "5" to sealedDto(day = 20_632, detail = "restaurado", createdAt = 100)
        repository.restore()

        val newId = database.journalDao().insert(
            JournalEntryRow(docId = "nombre-propio", day = 20_640, kind = "t01", detail = "nuevo", createdAt = 300)
        )

        // Los nombres remotos ya no se derivan del id local, así que un registro
        // nuevo jamás puede caer sobre el documento de otro (ADR 2026-10-06).
        val names = database.journalDao().entries().map { it.docId }
        assertEquals(names.size, names.toSet().size)
        assertTrue(database.journalDao().entryById(newId)!!.docId == "nombre-propio")
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
            schemaVersion = 3L
        )
        remote.remoteDocs += "4" to sealedDto(day = 20_632, detail = "futuro", createdAt = 60, schemaVersion = 5L)

        val result = repository.restore()

        assertTrue(result.isSuccess)
        assertEquals(listOf("1"), database.journalDao().entries().map { it.docId })
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
