package com.auroraclima.app.questionnaire.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.questionnaire.data.local.RemovedOutcomeRow
import com.auroraclima.app.questionnaire.data.remote.EncryptedOutcomeDto
import com.auroraclima.app.records.data.ReversiblePayloadCipher
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
 * RF-31 + RF-39: la sync de resultados guardados sella con la DEK recuperable y
 * sube SOLO blobs; la restauración repone únicamente sobre un almacén vacío,
 * con el MISMO id y fecha, y omite en silencio lo ilegible. Bajo Robolectric
 * por `org.json` y Room en memoria (mismo criterio que el historial).
 */
@RunWith(RobolectricTestRunner::class)
class FirestoreOutcomeSyncRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, GuidanceDatabase::class.java).build()
    private val storeAccess = GuidanceStoreAccess { database }
    private val remote = FakeOutcomeSyncRemoteSource()
    private val cipher = ReversiblePayloadCipher()
    private val keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = 4)
    private val repository = FirestoreOutcomeSyncRepository(remote, cipher, storeAccess, keyManager)

    @After
    fun tearDown() {
        database.close()
    }

    private var nextDocId = 0

    /** Fila como la crea el repositorio real: con su nombre de documento propio. */
    private suspend fun seedRow(
        label: String = "Es importante que busques apoyo pronto",
        savedAt: Long = 1_750_000_000_000
    ): Long = database.outcomeDao().insert(
        OutcomeRow(
            docId = "doc-${++nextDocId}",
            savedAt = savedAt,
            rank = 3,
            label = label,
            tags = """["tipo a","tipo b"]"""
        )
    )

    @Test
    fun `el payload nunca viaja en claro y el docId es el nombre propio del guardado`() = runTest {
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.uploaded.size)
        val (docId, dto) = remote.uploaded[0]
        // El nombre del documento es el propio del guardado, no su id local.
        assertEquals(database.outcomeDao().outcomeById(id)!!.docId, docId)

        val cleartext = "Es importante que busques apoyo pronto"
        assertFalse(dto.ciphertext.contains(cleartext))
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains(cleartext))
        assertEquals(1L, dto.schemaVersion)

        // Round-trip: el payload conserva fecha, rank, label y tags (RF-39).
        val payload = cipher.open(
            SealedPayload(
                ciphertext = Base64.getDecoder().decode(dto.ciphertext),
                iv = Base64.getDecoder().decode(dto.iv)
            )
        )
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        assertEquals(1_750_000_000_000L, json.getLong("at"))
        assertEquals(3, json.getInt("rank"))
        assertEquals(cleartext, json.getString("label"))
        assertEquals(2, json.getJSONArray("tags").length())
    }

    @Test
    fun `el exito deja constancia de la generacion y borrar propaga el docId`() = runTest {
        val id = seedRow()

        repository.push(id)
        assertEquals(0, database.outcomeDao().pendingSync(4).size)

        val docId = database.outcomeDao().outcomeById(id)!!.docId
        repository.remove(docId)
        assertEquals(listOf(docId), remote.deleted)
    }

    @Test
    fun `la restauracion repone con su nombre remoto solo sobre un almacen vacio`() = runTest {
        val id = seedRow()
        repository.push(id)
        database.outcomeDao().deleteById(id)

        val restored = repository.restore()

        assertTrue(restored.isSuccess)
        val rows = database.outcomeDao().outcomes()
        assertEquals(1, rows.size)
        // El id local lo asigna la base; lo que se conserva es el nombre remoto.
        assertEquals("doc-1", rows[0].docId)
        assertEquals(1_750_000_000_000L, rows[0].savedAt)
        assertEquals(4L, rows[0].syncedGeneration)

        // Con datos locales presentes, la restauración NO mezcla (RF-39).
        remote.remoteDocs.clear()
        seedRow(label = "local vigente")
        repository.push(database.outcomeDao().outcomes().first { it.label == "local vigente" }.id)
        assertTrue(repository.restore().isSuccess)
        assertEquals(2, database.outcomeDao().count())
    }

    @Test
    fun `un blob ilegible o de esquema desconocido se omite sin tumbar la restauracion`() = runTest {
        remote.remoteDocs += "9" to EncryptedOutcomeDto(ciphertext = "no-base64!!", iv = "x", schemaVersion = 1L)
        remote.remoteDocs += "8" to EncryptedOutcomeDto(
            ciphertext = Base64.getEncoder().encodeToString(ByteArray(4)),
            iv = Base64.getEncoder().encodeToString(ByteArray(12)),
            schemaVersion = 99L
        )

        val restored = repository.restore()

        assertTrue(restored.isSuccess)
        assertEquals(0, database.outcomeDao().count())
    }

    @Test
    fun `un guardado eliminado en el otro dispositivo desaparece aqui`() = runTest {
        // Subió, una lectura del servidor lo vio, y después desapareció: lo
        // eliminaron allá. La primera reconciliación es la que deja constancia
        // de haberlo visto; subir no basta, porque subir solo encola (RF-38).
        val id = seedRow()
        repository.push(id)
        repository.reconcile().getOrThrow()
        remote.remoteDocs.clear()

        repository.reconcile().getOrThrow()

        assertEquals(emptyList<Long>(), database.outcomeDao().outcomes().map { it.id })
    }

    @Test
    fun `un guardado subido sin confirmar del servidor no se borra`() = runTest {
        // Subir encola; si ninguna lectura del servidor lo ha visto todavía, su
        // ausencia no autoriza a borrarlo (auditoría 2026-10-06).
        val id = seedRow()
        repository.push(id)
        remote.remoteDocs.clear()

        repository.reconcile().getOrThrow()

        assertEquals(listOf(id), database.outcomeDao().outcomes().map { it.id })
    }

    @Test
    fun `un guardado que nunca subio se conserva`() = runTest {
        val id = seedRow()

        repository.reconcile().getOrThrow()

        assertEquals(listOf(id), database.outcomeDao().outcomes().map { it.id })
    }

    @Test
    fun `un guardado hecho en el otro dispositivo aparece aqui`() = runTest {
        val id = seedRow()
        repository.push(id)
        database.outcomeDao().deleteById(id)
        // Se eliminó la fila local SIN dejar constancia (no fue un borrado de
        // la usuaria): la reconciliación debe traerlo de vuelta.

        repository.reconcile().getOrThrow()

        assertEquals(1, database.outcomeDao().outcomes().size)
    }

    @Test
    fun `lo eliminado aqui sin conexion no vuelve a bajarse`() = runTest {
        val id = seedRow()
        repository.push(id)
        val docId = database.outcomeDao().outcomeById(id)!!.docId
        database.outcomeDao().deleteById(id)
        database.removedOutcomeDao().insert(
            RemovedOutcomeRow(docId = docId, at = System.currentTimeMillis())
        )

        repository.reconcile().getOrThrow()

        assertEquals(emptyList<Long>(), database.outcomeDao().outcomes().map { it.id })
        assertEquals(listOf(docId), database.removedOutcomeDao().docIds())
    }

    @Test
    fun `sin conexion la reconciliacion no toca nada`() = runTest {
        val id = seedRow()
        repository.push(id)
        remote.online = false

        assertTrue(repository.reconcile().isFailure)
        assertEquals(listOf(id), database.outcomeDao().outcomes().map { it.id })
    }

    @Test
    fun `un nombre legado reutilizado por otro resultado no provoca su borrado`() = runTest {
        // Antes de la migración v2 el nombre era el id local, así que dos
        // teléfonos pudieron dar el mismo a resultados distintos. Si eliminar
        // el «1» en uno borrase el «1» del otro, se perdería un resultado que
        // la usuaria nunca tocó. La fecha de guardado los distingue.
        val id = database.outcomeDao().insert(
            OutcomeRow(docId = "1", savedAt = 1_000, rank = 2, label = "el mío", tags = "[]")
        )
        remote.remoteDocs += "1" to sealed(savedAt = 9_999, label = "el del otro teléfono")
        repository.reconcile().getOrThrow()
        remote.remoteDocs.clear()

        repository.reconcile().getOrThrow()

        assertEquals(listOf(id), database.outcomeDao().outcomes().map { it.id })
        assertEquals("el mío", database.outcomeDao().outcomeById(id)?.label)
    }

    /** Documento «en el servidor» sellado como lo sella el dispositivo que lo subió. */
    private fun sealed(savedAt: Long, label: String): EncryptedOutcomeDto {
        val json = JSONObject()
            .put("at", savedAt)
            .put("rank", 2)
            .put("label", label)
            .put("tags", org.json.JSONArray())
        val sealed = cipher.seal(json.toString().toByteArray(Charsets.UTF_8))
        return EncryptedOutcomeDto(
            ciphertext = Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = Base64.getEncoder().encodeToString(sealed.iv),
            schemaVersion = 1L
        )
    }
}
