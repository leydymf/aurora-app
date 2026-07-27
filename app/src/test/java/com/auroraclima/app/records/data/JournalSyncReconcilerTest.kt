package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migración RF-39 y sus tolerancias: el reconciliador re-sube todo registro sin
 * constancia de la generación vigente. Completa, interrumpida a mitad, repetida
 * y rotación de DEK — el mismo mecanismo cubre las cuatro.
 */
@RunWith(RobolectricTestRunner::class)
class JournalSyncReconcilerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
    private val storeAccess = JournalStoreAccess { database }
    private val remote = FakeJournalSyncRemoteSource()
    private val keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = 1)
    private val syncRepository =
        FirestoreJournalSyncRepository(remote, ReversiblePayloadCipher(), storeAccess, keyManager)
    private val store = AttachmentStore(context, ReversibleMediaCipher())
    private val mediaRemote = FakeJournalMediaRemoteSource()
    private val mediaSyncRepository = FirestoreJournalMediaSyncRepository(
        context = context,
        remote = mediaRemote,
        storeAccess = storeAccess,
        store = store,
        travelCipher = ReversibleRecoverableMediaCipher(),
        metaCipher = ReversiblePayloadCipher(),
        keyManager = keyManager
    )
    private val reconciler = JournalSyncReconciler(storeAccess, syncRepository, mediaSyncRepository, keyManager)

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedRows(count: Int): List<Long> = (1..count).map { index ->
        database.journalDao().insert(
            JournalEntryRow(
                day = 20_600L + index,
                kind = "economic",
                detail = "registro $index",
                createdAt = index.toLong()
            )
        )
    }

    @Test
    fun `migracion completa re-sube todo el historial y deja constancia`() = runTest {
        // Tras actualizar, la columna nueva nace en 0: todo el historial v1
        // queda pendiente y sube re-cifrado sobre sus MISMOS docIds.
        val ids = seedRows(3)

        reconciler.restore()

        assertEquals(ids.map { it.toString() }, remote.uploaded.map { it.first })
        assertTrue(remote.uploaded.all { it.second.schemaVersion == 2L })
        ids.forEach { id ->
            assertEquals(1L, database.journalDao().entryById(id)?.syncedGeneration)
        }
    }

    @Test
    fun `migracion interrumpida conserva el avance y el reintento re-sube solo el resto`() = runTest {
        val ids = seedRows(3)
        // La red se cae tras la primera subida: los push restantes fallan.
        remote.failAfterUploads = 1

        reconciler.restore()

        assertEquals(1, remote.uploaded.size)
        assertEquals(1L, database.journalDao().entryById(ids[0])?.syncedGeneration)
        assertEquals(0L, database.journalDao().entryById(ids[1])?.syncedGeneration)

        // «Siguiente login»: la red volvió; solo lo pendiente vuelve a subir.
        remote.failAfterUploads = null
        reconciler.restore()

        assertEquals(ids.map { it.toString() }, remote.uploaded.map { it.first })
        ids.forEach { id ->
            assertEquals(1L, database.journalDao().entryById(id)?.syncedGeneration)
        }
    }

    @Test
    fun `una reconciliacion repetida no re-sube nada`() = runTest {
        seedRows(2)
        reconciler.restore()
        val uploadsAfterFirst = remote.uploaded.size

        reconciler.restore()

        assertEquals(uploadsAfterFirst, remote.uploaded.size)
    }

    @Test
    fun `una rotacion de DEK deja todo desfasado y el historial completo vuelve a subir`() = runTest {
        // Respaldo reiniciado (generación nueva): las constancias viejas ya no
        // valen y el dispositivo con los datos re-publica todo — es la vía por
        // la que un reinicio recupera el historial si este teléfono sobrevive.
        val ids = seedRows(2)
        reconciler.restore()
        keyManager.generation = 2

        reconciler.restore()

        assertEquals(2 * ids.size, remote.uploaded.size)
        ids.forEach { id ->
            assertEquals(2L, database.journalDao().entryById(id)?.syncedGeneration)
        }
    }

    @Test
    fun `en un dispositivo nuevo repone desde la nube y no re-sube lo repuesto`() = runTest {
        // RF-39 criterio 1, punta a punta en la reconciliación: BD vacía +
        // documentos v2 en el servidor → registros repuestos con constancia,
        // sin re-subir nada (ya están en la nube con esta DEK).
        val cipher = ReversiblePayloadCipher()
        val payload = org.json.JSONObject()
            .put("day", 20_632L)
            .put("kind", "economic")
            .put("detail", "registro remoto")
            .put("at", 100L)
        val sealed = cipher.seal(payload.toString().toByteArray(Charsets.UTF_8))
        remote.remoteDocs += "7" to com.auroraclima.app.records.data.remote.EncryptedEntryDto(
            ciphertext = java.util.Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = java.util.Base64.getEncoder().encodeToString(sealed.iv),
            schemaVersion = 2L
        )

        reconciler.restore()

        assertEquals(listOf(7L), database.journalDao().entries().map { it.id })
        assertEquals("registro remoto", database.journalDao().entryById(7)?.detail)
        assertEquals(1L, database.journalDao().entryById(7)?.syncedGeneration)
        assertTrue(remote.uploaded.isEmpty())
    }

    @Test
    fun `sin DEK establecida no hace nada`() = runTest {
        seedRows(2)
        keyManager.generation = null

        reconciler.restore()

        assertTrue(remote.uploaded.isEmpty())
        assertTrue(mediaRemote.headers.isEmpty())
    }

    @Test
    fun `los adjuntos pendientes tambien se reconcilian y quedan con constancia`() = runTest {
        // Migración RF-39 de evidencias: los adjuntos existentes (synced_gen 0)
        // suben troceados en el primer login tras actualizar.
        val ids = seedRows(1)
        val ref = store.newRef()
        store.write(ref) { it.write("evidencia local".toByteArray(Charsets.UTF_8)) }
        val attachmentId = database.attachmentDao().insert(
            com.auroraclima.app.records.data.local.AttachmentRow(
                owner = ids[0],
                ref = ref,
                kind = "image/jpeg",
                createdAt = 5
            )
        )

        reconciler.restore()

        assertEquals(setOf(ref), mediaRemote.headers.keys)
        assertEquals(1L, database.attachmentDao().byId(attachmentId)?.syncedGeneration)

        // Repetir no re-sube (constancia al día).
        val uploadsAfterFirst = mediaRemote.uploadCalls
        reconciler.restore()
        assertEquals(uploadsAfterFirst, mediaRemote.uploadCalls)
    }

    @Test
    fun `en un dispositivo nuevo las evidencias se reponen despues de los registros`() = runTest {
        // «Dispositivo A»: registro + adjunto subidos.
        val ids = seedRows(1)
        val ref = store.newRef()
        val content = "evidencia restaurable".toByteArray(Charsets.UTF_8)
        store.write(ref) { it.write(content) }
        database.attachmentDao().insert(
            com.auroraclima.app.records.data.local.AttachmentRow(
                owner = ids[0],
                ref = ref,
                kind = "image/jpeg",
                createdAt = 5
            )
        )
        reconciler.restore()
        // «Dispositivo B»: nada local.
        database.journalDao().deleteById(ids[0])
        database.attachmentDao().byOwner(ids[0]).forEach { database.attachmentDao().deleteById(it.id) }
        store.deleteAll()

        reconciler.restore()

        // El registro volvió y su evidencia también, íntegra y legible.
        assertEquals(1, database.journalDao().entries().size)
        val restored = database.attachmentDao().byOwner(ids[0]).single()
        assertEquals(ref, restored.ref)
        org.junit.Assert.assertArrayEquals(content, store.read(ref) { it.readBytes() })
    }
}
