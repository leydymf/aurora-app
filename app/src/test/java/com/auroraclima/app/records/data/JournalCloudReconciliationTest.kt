package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.data.local.RemovedRow
import com.auroraclima.app.records.data.remote.EncryptedEntryDto
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Reconciliación del historial CON la nube (ADR 2026-10-06): sobre un
 * dispositivo que ya tiene datos, trae lo que se creó, editó o eliminó en el
 * otro teléfono y conserva lo que aquí está pendiente de subir.
 *
 * Las cuatro reglas que se fijan aquí son las que pidió la usuaria:
 * eliminado allá se elimina aquí; editado allá y más reciente se adopta;
 * nunca subido se conserva; y, ante el conflicto «eliminado allá» contra
 * «editado aquí sin subir», gana la eliminación.
 */
@RunWith(RobolectricTestRunner::class)
class JournalCloudReconciliationTest {
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

    /** Registro local con su nombre de documento propio, como lo crea el repositorio real. */
    private suspend fun seedLocal(
        docId: String,
        detail: String = "lo que pasó",
        updatedAt: Long = 100,
        syncedGeneration: Long = 4,
        cloudSeen: Boolean = true,
        createdAt: Long = 100,
        cloudSeenGeneration: Long = if (cloudSeen) 4 else 0
    ): Long = database.journalDao().insert(
        JournalEntryRow(
            docId = docId,
            day = 20_632,
            kind = "t04",
            label = "Cuarta",
            detail = detail,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncedGeneration = syncedGeneration,
            cloudSeenGeneration = cloudSeenGeneration
        )
    )

    /** Documento «en el servidor», sellado como lo sella el dispositivo que lo subió. */
    private fun seedRemote(docId: String, detail: String, updatedAt: Long, createdAt: Long = 100L) {
        val json = JSONObject()
            .put("day", 20_632L)
            .put("kind", "t04")
            .put("label", "Cuarta")
            .put("detail", detail)
            .put("at", createdAt)
            .put("up", updatedAt)
        val sealed = cipher.seal(json.toString().toByteArray(Charsets.UTF_8))
        remote.remoteDocs += docId to EncryptedEntryDto(
            ciphertext = Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = Base64.getEncoder().encodeToString(sealed.iv),
            schemaVersion = 4L
        )
    }

    @Test
    fun `un registro eliminado en el otro dispositivo se elimina aqui`() = runTest {
        // Subió con esta misma DEK (constancia al día) y ya no está en la nube:
        // la única explicación es que lo eliminaron allá.
        val id = seedLocal(docId = "a")

        val removed = repository.reconcile().getOrThrow()

        assertEquals(listOf(id), removed)
        assertNull(database.journalDao().entryById(id))
    }

    @Test
    fun `un registro que nunca se subio se conserva aunque no este en la nube`() = runTest {
        // Creado sin conexión: ninguna lectura del servidor lo ha visto, así
        // que su ausencia significa «todavía no subió», no «lo borraron».
        val id = seedLocal(docId = "b", syncedGeneration = 0, cloudSeen = false)

        val removed = repository.reconcile().getOrThrow()

        assertTrue(removed.isEmpty())
        assertNotNull(database.journalDao().entryById(id))
    }

    @Test
    fun `una edicion mas reciente del otro dispositivo se adopta`() = runTest {
        val id = seedLocal(docId = "c", detail = "versión vieja", updatedAt = 100)
        seedRemote(docId = "c", detail = "versión corregida allá", updatedAt = 200)

        repository.reconcile().getOrThrow()

        val row = requireNotNull(database.journalDao().entryById(id))
        assertEquals("versión corregida allá", row.detail)
        assertEquals(200L, row.updatedAt)
        // Lo adoptado ya está en la nube con esta DEK: no se re-sube.
        assertEquals(4L, row.syncedGeneration)
    }

    @Test
    fun `una edicion local mas reciente no se pisa con la copia vieja de la nube`() = runTest {
        val id = seedLocal(docId = "d", detail = "lo que escribí aquí", updatedAt = 300, syncedGeneration = 0)
        seedRemote(docId = "d", detail = "versión vieja de la nube", updatedAt = 150)

        repository.reconcile().getOrThrow()

        assertEquals("lo que escribí aquí", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `ante eliminado alla y editado aqui sin subir, gana la eliminacion`() = runTest {
        // Conflicto declarado en el ADR: si la usuaria lo borró en su otro
        // teléfono, lo quiso fuera; una edición local sin subir no lo rescata.
        // La fila SÍ estuvo en la nube (una lectura la vio); la edición local
        // posterior la dejó pendiente de subir.
        val id = seedLocal(docId = "e", detail = "lo de antes", updatedAt = 100, cloudSeen = true)
        database.journalDao()
            .update(id = id, day = 20_632, kind = "t04", label = "Cuarta", detail = "editado aquí", updatedAt = 500)

        val removed = repository.reconcile().getOrThrow()

        assertEquals(listOf(id), removed)
        assertNull(database.journalDao().entryById(id))
    }

    @Test
    fun `un registro creado en el otro dispositivo aparece aqui`() = runTest {
        seedLocal(docId = "f")
        seedRemote(docId = "g", detail = "lo escribí en el otro teléfono", updatedAt = 400)

        repository.reconcile().getOrThrow()

        val incoming = database.journalDao().entryByDocId("g")
        assertNotNull(incoming)
        assertEquals("lo escribí en el otro teléfono", incoming?.detail)
        assertEquals(4L, incoming?.syncedGeneration)
    }

    @Test
    fun `lo eliminado aqui sin conexion no vuelve a bajarse mientras el borrado sigue en cola`() = runTest {
        // El documento sigue en la nube porque el `delete` no ha salido; la
        // constancia local es lo que impide resucitarlo.
        seedRemote(docId = "h", detail = "ya lo borré", updatedAt = 100)
        database.removedDao().insert(
            RemovedRow(docId = "h", scope = RemovedRow.SCOPE_ENTRY, at = System.currentTimeMillis())
        )

        repository.reconcile().getOrThrow()

        assertNull(database.journalDao().entryByDocId("h"))
        assertEquals(listOf("h"), database.removedDao().docIds(RemovedRow.SCOPE_ENTRY))
    }

    @Test
    fun `cuando la nube confirma el borrado la constancia se retira`() = runTest {
        // Ya asentada: una constancia recién puesta espera a que la cola
        // durable aplique su borrado antes de poder retirarse (A-6).
        database.removedDao().insert(
            RemovedRow(
                docId = "i",
                scope = RemovedRow.SCOPE_ENTRY,
                at = System.currentTimeMillis() - 2 * 60 * 60 * 1000
            )
        )

        repository.reconcile().getOrThrow()

        assertTrue(database.removedDao().docIds(RemovedRow.SCOPE_ENTRY).isEmpty())
    }

    @Test
    fun `sin conexion no se toca nada`() = runTest {
        remote.online = false
        val id = seedLocal(docId = "j", detail = "intacto")

        val result = repository.reconcile()

        assertTrue(result.isFailure)
        assertEquals("intacto", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `sin llave establecida no se reconcilia`() = runTest {
        keyManager.generation = null
        val id = seedLocal(docId = "k", detail = "intacto")

        assertTrue(repository.reconcile().isFailure)
        assertNotNull(database.journalDao().entryById(id))
    }

    @Test
    fun `un registro subido sin conexion no se borra aunque el servidor aun no lo tenga`() = runTest {
        // EL CASO QUE IMPORTA (auditoría 2026-10-06). Subir solo ENCOLA: la
        // fila queda con la constancia de la generación vigente aunque el
        // servidor todavía no tenga el documento. Si eso bastara para borrar,
        // crear un registro sin conexión y volver a abrir la lista lo
        // destruiría junto con sus adjuntos.
        remote.online = false
        val id = seedLocal(docId = "z", detail = "escrito sin señal", cloudSeen = false)
        repository.push(id)
        assertEquals(4L, database.journalDao().entryById(id)?.syncedGeneration)
        remote.online = true
        // La red vuelve pero la escritura encolada todavía no llegó al servidor.
        remote.queued.clear()

        val removed = repository.reconcile().getOrThrow()

        assertTrue(removed.isEmpty())
        assertEquals("escrito sin señal", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `un documento presente pero ilegible no se confunde con uno eliminado`() = runTest {
        // Al documento le falta un campo, así que el DTO llega nulo; su id SÍ
        // viaja, y eso es lo que impide borrar la fila local.
        val id = seedLocal(docId = "y")
        remote.remoteDocs += "y" to null

        val removed = repository.reconcile().getOrThrow()

        assertTrue(removed.isEmpty())
        assertNotNull(database.journalDao().entryById(id))
    }

    @Test
    fun `un nombre legado reutilizado por otro registro no pisa el local`() = runTest {
        // Antes del ADR 2026-10-06 el nombre del documento era el id local, así
        // que dos dispositivos podían asignar el mismo a registros DISTINTOS.
        // La fecha de creación distinta los delata: no se adopta.
        val id = seedLocal(docId = "7", detail = "mi registro", createdAt = 100, updatedAt = 100)
        seedRemote(docId = "7", detail = "el registro del otro teléfono", updatedAt = 900, createdAt = 555)

        repository.reconcile().getOrThrow()

        assertEquals("mi registro", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `una vez visto en el servidor, la fila queda marcada`() = runTest {
        val id = seedLocal(docId = "w", cloudSeen = false)
        seedRemote(docId = "w", detail = "lo que pasó", updatedAt = 100)

        repository.reconcile().getOrThrow()

        assertEquals(4L, database.journalDao().entryById(id)?.cloudSeenGeneration)
    }

    @Test
    fun `tras reiniciar el respaldo la reconciliacion no borra nada`() = runTest {
        // RF-39: una contraseña restablecida sin copia local purga el servidor y
        // sube la generación. Si la reconciliación siguiente borrara lo que «ya
        // no está en la nube», se llevaría TODO el historial local — lo
        // contrario de lo que promete el aviso de RF-46 (auditoría A-1).
        // Subió y se vio en la generación 4; la purga sube a la 5 y, aunque el
        // push vuelva a estampar la 5 en `synced_gen`, la marca de «visto»
        // sigue siendo de la era anterior y no autoriza nada.
        val id = seedLocal(docId = "r", detail = "mi historial", cloudSeenGeneration = 4, syncedGeneration = 5)
        keyManager.generation = 5

        val removed = repository.reconcile().getOrThrow()

        assertTrue(removed.isEmpty())
        assertEquals("mi historial", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `un nombre legado reutilizado por otro registro no provoca su borrado`() = runTest {
        // El otro teléfono eliminó SU registro «1»; el de aquí, con el mismo
        // nombre legado pero distinta fecha de creación, no es ese (A-2). Como
        // su identidad nunca se verificó, nunca se marcó como visto.
        val id = seedLocal(docId = "1", detail = "el mío", createdAt = 100, cloudSeen = false)
        seedRemote(docId = "1", detail = "el del otro teléfono", updatedAt = 100, createdAt = 555)
        repository.reconcile().getOrThrow()
        remote.remoteDocs.clear()

        val removed = repository.reconcile().getOrThrow()

        assertTrue(removed.isEmpty())
        assertEquals("el mío", database.journalDao().entryById(id)?.detail)
    }

    @Test
    fun `la constancia recien puesta no se retira en la pasada siguiente`() = runTest {
        // Si se retirara enseguida, una subida encolada desde antes podría
        // resucitar el documento y volvería a bajarse (A-6).
        val id = seedLocal(docId = "s", cloudSeen = true)
        repository.reconcile().getOrThrow()
        assertNull(database.journalDao().entryById(id))

        repository.reconcile().getOrThrow()

        assertEquals(listOf("s"), database.removedDao().docIds(RemovedRow.SCOPE_ENTRY))
    }
}
