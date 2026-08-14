package com.auroraclima.app.questionnaire.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
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

    private suspend fun seedRow(
        label: String = "Es importante que busques apoyo pronto",
        savedAt: Long = 1_750_000_000_000
    ): Long = database.outcomeDao().insert(
        OutcomeRow(savedAt = savedAt, rank = 3, label = label, tags = """["tipo a","tipo b"]""")
    )

    @Test
    fun `el payload nunca viaja en claro y el docId es el id local`() = runTest {
        val id = seedRow()

        val result = repository.push(id)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.uploaded.size)
        val (docId, dto) = remote.uploaded[0]
        assertEquals(id.toString(), docId)

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

        repository.remove(id)
        assertEquals(listOf(id.toString()), remote.deleted)
    }

    @Test
    fun `la restauracion repone con el mismo id solo sobre un almacen vacio`() = runTest {
        val id = seedRow()
        repository.push(id)
        database.outcomeDao().deleteById(id)

        val restored = repository.restore()

        assertTrue(restored.isSuccess)
        val rows = database.outcomeDao().outcomes()
        assertEquals(1, rows.size)
        assertEquals(id, rows[0].id)
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
}
