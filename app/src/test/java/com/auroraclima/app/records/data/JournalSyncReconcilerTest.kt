package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.ui.FakeJournalAttachmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val attachmentRepository = FakeJournalAttachmentRepository()
    private val reconciler = JournalSyncReconciler(
        storeAccess,
        syncRepository,
        mediaSyncRepository,
        attachmentRepository,
        keyManager
    )

    @After
    fun tearDown() {
        database.close()
    }

    /** Filas como las crea el repositorio real: con su nombre de documento propio. */
    private suspend fun seedRows(count: Int): List<Long> = (1..count).map { index ->
        database.journalDao().insert(
            JournalEntryRow(
                docId = "doc-$index",
                day = 20_600L + index,
                kind = "t04",
                detail = "registro $index",
                createdAt = index.toLong(),
                updatedAt = index.toLong()
            )
        )
    }

    @Test
    fun `migracion completa re-sube todo el historial y deja constancia`() = runTest {
        // Tras actualizar, la columna nueva nace en 0: todo el historial v1
        // queda pendiente y sube re-cifrado sobre sus MISMOS docIds.
        val ids = seedRows(3)

        reconciler.restore()

        assertEquals(ids.indices.map { "doc-${it + 1}" }, remote.uploaded.map { it.first })
        assertTrue(remote.uploaded.all { it.second.schemaVersion == 4L })
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

        assertEquals(ids.indices.map { "doc-${it + 1}" }, remote.uploaded.map { it.first })
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
        // documentos v2 (dispositivo anterior, código legado) en el servidor →
        // registros repuestos con su id de tema y constancia, sin re-subir nada
        // (ya están en la nube con esta DEK).
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

        val restored = database.journalDao().entries().single()
        assertEquals("7", restored.docId)
        assertEquals("registro remoto", restored.detail)
        assertEquals("t04", restored.kind)
        assertEquals(1L, restored.syncedGeneration)
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
    fun `la migracion de adjuntos interrumpida conserva el avance y el reintento sube el resto`() = runTest {
        // Verificación D2 H2: espejo del test de registros, ahora para media.
        val ids = seedRows(1)
        val refs = (1..3).map { index ->
            val ref = store.newRef()
            store.write(ref) { it.write("evidencia $index".toByteArray(Charsets.UTF_8)) }
            database.attachmentDao().insert(
                com.auroraclima.app.records.data.local.AttachmentRow(
                    owner = ids[0],
                    ref = ref,
                    kind = "image/jpeg",
                    createdAt = index.toLong()
                )
            )
            ref
        }
        // La red cae tras la primera subida de adjunto.
        mediaRemote.failAfterUploads = 1

        reconciler.restore()

        assertEquals(1, mediaRemote.headers.size)
        assertEquals(1L, database.attachmentDao().byId(1)?.syncedGeneration)
        assertEquals(0L, database.attachmentDao().byId(2)?.syncedGeneration)

        // «Siguiente login»: la red volvió; solo lo pendiente vuelve a subir.
        mediaRemote.failAfterUploads = null
        reconciler.restore()

        assertEquals(refs.toSet(), mediaRemote.headers.keys)
        (1L..3L).forEach { id ->
            assertEquals(1L, database.attachmentDao().byId(id)?.syncedGeneration)
        }
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
        val owner = database.journalDao().entries().single()
        val restored = database.attachmentDao().byOwner(owner.id).single()
        assertEquals(ref, restored.ref)
        org.junit.Assert.assertArrayEquals(content, store.read(ref) { it.readBytes() })
    }

    @Test
    fun `la reconciliacion baja y borra antes de subir lo pendiente`() = runTest {
        // El orden importa y este test lo discrimina: la fila ya estuvo en la
        // nube (`cloudSeen`), se editó aquí sin subir (`pending`) y allá la
        // eliminaron. Si se subiera primero, la edición se re-subiría y el
        // registro que la usuaria borró reaparecería (segunda auditoría del
        // 2026-10-06). Debe borrarse y NO subir nada.
        val id = database.journalDao().insert(
            JournalEntryRow(
                docId = "doc-borrado-alla",
                day = 20_600,
                kind = "t04",
                detail = "editado aquí sin subir",
                createdAt = 1,
                updatedAt = 2,
                syncedGeneration = 1,
                pending = true,
                cloudSeenGeneration = 1
            )
        )

        reconciler.reconcile()

        assertNull(database.journalDao().entryById(id))
        assertTrue(remote.uploaded.none { it.first == "doc-borrado-alla" })
    }

    @Test
    fun `lo pendiente que nadie elimino si se sube al reconciliar`() = runTest {
        val ids = seedRows(2)

        reconciler.reconcile()

        assertEquals(ids.indices.map { "doc-${it + 1}" }, remote.uploaded.map { it.first })
        assertEquals(2, database.journalDao().entries().size)
    }

    @Test
    fun `los adjuntos de un registro eliminado alla se borran con el`() = runTest {
        val ids = seedRows(1)
        val ref = store.newRef()
        store.write(ref) { it.write("evidencia".toByteArray(Charsets.UTF_8)) }
        database.attachmentDao().insert(
            com.auroraclima.app.records.data.local.AttachmentRow(
                owner = ids[0],
                ref = ref,
                kind = "image/jpeg",
                createdAt = 5
            )
        )
        // Primero sube; la reconciliación siguiente lo VE en el servidor y lo
        // marca; solo entonces su desaparición significa «lo eliminaron allá»
        // (son dos ciclos a propósito: ver P-86 f).
        reconciler.reconcile()
        reconciler.reconcile()
        remote.remoteDocs.clear()
        mediaRemote.headers.clear()

        reconciler.reconcile()

        assertEquals(0, database.journalDao().entries().size)
        assertEquals(listOf(ids[0]), attachmentRepository.deletedOwners)
    }

    @Test
    fun `sin llave la reconciliacion no toca nada`() = runTest {
        val ids = seedRows(1)
        keyManager.generation = null

        reconciler.reconcile()

        assertEquals(1, database.journalDao().entries().size)
        assertEquals(ids.size, database.journalDao().entries().size)
        assertTrue(remote.uploaded.isEmpty())
    }
}
