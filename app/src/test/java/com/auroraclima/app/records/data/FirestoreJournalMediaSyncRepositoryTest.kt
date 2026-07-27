package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.records.data.local.AttachmentRow
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Sync troceada de evidencias (RF-39): subida re-cifrada por stream con la DEK,
 * trozos bajo el límite de documento, metadatos sellados, constancia de
 * generación, y la restauración inversa completa sobre un almacén vacío.
 * Cifradores reversibles y fuente remota falsa; Room en memoria.
 */
@RunWith(RobolectricTestRunner::class)
class FirestoreJournalMediaSyncRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
    private val storeAccess = JournalStoreAccess { database }
    private val store = AttachmentStore(context, ReversibleMediaCipher())
    private val remote = FakeJournalMediaRemoteSource()
    private val keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = 2)
    private val repository = FirestoreJournalMediaSyncRepository(
        context = context,
        remote = remote,
        storeAccess = storeAccess,
        store = store,
        travelCipher = ReversibleRecoverableMediaCipher(),
        metaCipher = ReversiblePayloadCipher(),
        keyManager = keyManager
    )

    private val mediaDir = File(context.filesDir, "media_store")

    @After
    fun tearDown() {
        database.close()
        mediaDir.deleteRecursively()
    }

    /** Un adjunto reclamado con contenido real en el almacén local. */
    private suspend fun seedAttachment(
        owner: Long = 7,
        content: ByteArray = "contenido pequeño".toByteArray(Charsets.UTF_8)
    ): Long {
        database.journalDao().insert(
            JournalEntryRow(id = owner, day = 1, kind = "economic", detail = "x", createdAt = 1)
        )
        val ref = store.newRef()
        store.write(ref) { it.write(content) }
        return database.attachmentDao().insert(
            AttachmentRow(owner = owner, ref = ref, kind = "image/jpeg", createdAt = 99)
        )
    }

    @Test
    fun `la subida trocea bajo el limite y nada viaja en claro`() = runTest {
        // 1,4 MB aleatorios → 3 trozos de ≤640 KiB (límite de 1 MiB en base64).
        val content = ByteArray(1_400_000).also { SecureRandom().nextBytes(it) }
        val id = seedAttachment(content = content)

        repository.push(id).getOrThrow()

        assertEquals(1, remote.headers.size)
        val (ref, header) = remote.headers.entries.first().toPair()
        assertEquals(3, header.partCount)
        assertEquals(3, remote.parts.size)
        remote.parts.values.forEach { part ->
            assertTrue(Base64.getDecoder().decode(part).size <= FirestoreJournalMediaSyncRepository.CHUNK_BYTES)
        }
        // Reconstruyendo los trozos y deshaciendo el cifrado de viaje se
        // recupera el contenido EXACTO — y sin la llave, nada es legible.
        val sealed = (0 until header.partCount).flatMap {
            Base64.getDecoder().decode(remote.parts["${ref}_$it"]).toList()
        }.toByteArray()
        val opened = ReversibleRecoverableMediaCipher()
            .opening(sealed.inputStream()).use { it.readBytes() }
        assertArrayEquals(content, opened)
    }

    @Test
    fun `el exito deja constancia y los metadatos viajan sellados`() = runTest {
        val id = seedAttachment()

        repository.push(id).getOrThrow()

        assertEquals(2L, database.attachmentDao().byId(id)?.syncedGeneration)
        val header = remote.headers.values.first()
        // El JSON de metadatos NO está en claro en el campo…
        assertTrue(!header.meta.contains("image/jpeg"))
        // …pero abierto con el cifrador de payloads contiene dueño, tipo y fecha.
        val meta = org.json.JSONObject(
            ReversiblePayloadCipher().open(
                com.auroraclima.app.core.security.SealedPayload(
                    Base64.getDecoder().decode(header.meta),
                    Base64.getDecoder().decode(header.iv)
                )
            ).toString(Charsets.UTF_8)
        )
        assertEquals(7L, meta.getLong("owner"))
        assertEquals("image/jpeg", meta.getString("kind"))
        assertEquals(99L, meta.getLong("at"))
    }

    @Test
    fun `un adjunto en preparacion no sube`() = runTest {
        val ref = store.newRef()
        store.write(ref) { it.write(byteArrayOf(1)) }
        val id = database.attachmentDao().insert(
            AttachmentRow(owner = 0, ref = ref, kind = "image/jpeg", createdAt = 1)
        )

        repository.push(id).getOrThrow()

        assertTrue(remote.headers.isEmpty())
        assertEquals(0L, database.attachmentDao().byId(id)?.syncedGeneration)
    }

    @Test
    fun `sin DEK el push falla y el adjunto queda pendiente`() = runTest {
        keyManager.generation = null
        val id = seedAttachment()

        assertTrue(repository.push(id).isFailure)
        assertTrue(remote.headers.isEmpty())
        assertEquals(0L, database.attachmentDao().byId(id)?.syncedGeneration)
    }

    @Test
    fun `la restauracion repone el adjunto integro sobre un almacen vacio`() = runTest {
        // «Dispositivo A» sube…
        val content = ByteArray(900_000).also { SecureRandom().nextBytes(it) }
        val id = seedAttachment(content = content)
        repository.push(id).getOrThrow()
        val ref = database.attachmentDao().byId(id)!!.ref
        // …y el «dispositivo B» parte sin adjuntos locales pero con el registro
        // dueño ya restaurado (la reposición de registros corre antes).
        database.attachmentDao().deleteById(id)
        store.deleteAll()

        repository.restore().getOrThrow()

        val restored = database.attachmentDao().byOwner(7).single()
        assertEquals(ref, restored.ref)
        assertEquals("image/jpeg", restored.kind)
        assertEquals(99L, restored.createdAt)
        // Lo repuesto ya está en la nube: constancia puesta, no se re-sube.
        assertEquals(2L, restored.syncedGeneration)
        assertTrue(database.attachmentDao().pendingSync(2).isEmpty())
        // El archivo local re-sellado se lee byte a byte.
        assertArrayEquals(content, store.read(ref) { it.readBytes() })
    }

    @Test
    fun `la restauracion no repone sobre un almacen con adjuntos`() = runTest {
        val id = seedAttachment()
        repository.push(id).getOrThrow()

        // El almacén NO está vacío (el propio adjunto sigue): nada cambia.
        repository.restore().getOrThrow()

        assertEquals(1, database.attachmentDao().byOwner(7).size)
    }

    @Test
    fun `cabeceras huerfanas o de formato futuro se omiten sin romper`() = runTest {
        // Sube un adjunto de un registro que en B no existirá, y una cabecera
        // de esquema futuro: ninguno debe tumbar la restauración del resto.
        val goodContent = "legible".toByteArray(Charsets.UTF_8)
        val goodId = seedAttachment(owner = 7, content = goodContent)
        repository.push(goodId).getOrThrow()
        val orphanId = seedAttachment(owner = 8, content = byteArrayOf(9))
        repository.push(orphanId).getOrThrow()
        val futureRef = "ff00"
        remote.headers[futureRef] = remote.headers.values.first().copy(schemaVersion = 99)

        database.attachmentDao().deleteByIds(listOf(goodId, orphanId))
        store.deleteAll()
        // En B solo existe el registro 7 (el 8 jamás se restauró).
        database.journalDao().deleteById(8)

        repository.restore().getOrThrow()

        val restored = database.attachmentDao().byOwner(7)
        assertEquals(1, restored.size)
        assertNotNull(store.read(restored.single().ref) { it.readBytes() })
        assertTrue(database.attachmentDao().byOwner(8).isEmpty())
    }

    @Test
    fun `el borrado a ciegas cubre el adjunto mas grande permitido`() {
        // Revisión D2 M-4: MAX_BLIND_PARTS, CHUNK_BYTES y el tope de archivo
        // están acoplados entre clases — este invariante evita que un cambio
        // futuro deje trozos huérfanos en la nube sin que nada avise.
        val gcmOverheadBytes = 12 + 16 // nonce + tag
        val maxSealedBytes = com.auroraclima.app.records.domain.model.AttachmentPolicy.MAX_DOCUMENT_BYTES +
            gcmOverheadBytes
        val chunk = FirestoreJournalMediaSyncRepository.CHUNK_BYTES
        val maxParts = ((maxSealedBytes + chunk - 1) / chunk).toInt()

        assertTrue(
            com.auroraclima.app.records.data.remote.FirestoreJournalMediaRemoteSource.MAX_BLIND_PARTS >= maxParts
        )
    }

    @Test
    fun `un trozo faltante omite ese adjunto sin romper la restauracion`() = runTest {
        val id = seedAttachment()
        repository.push(id).getOrThrow()
        val ref = database.attachmentDao().byId(id)!!.ref
        database.attachmentDao().deleteById(id)
        store.deleteAll()
        // El trozo 0 se pierde (subida a medias de otra instalación).
        remote.parts.remove("${ref}_0")

        repository.restore().getOrThrow()

        assertTrue(database.attachmentDao().byOwner(7).isEmpty())
    }
}
