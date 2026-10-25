package com.auroraclima.app.questionnaire.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.FakeAccessCodePresence
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.records.data.ReversiblePayloadCipher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RF-31 + RF-39: tras cada login el reconciliador repone (dispositivo nuevo) y
 * re-sube los guardados con constancia desfasada — push perdidos y rotación de
 * DEK convergen en el mismo mecanismo, como en el historial.
 */
@RunWith(RobolectricTestRunner::class)
class OutcomeSyncReconcilerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, GuidanceDatabase::class.java).build()
    private val storeAccess = GuidanceStoreAccess { database }
    private val remote = FakeOutcomeSyncRemoteSource()
    private val keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = 4)
    private val accessCode = FakeAccessCodePresence()
    private val syncRepository =
        FirestoreOutcomeSyncRepository(remote, ReversiblePayloadCipher(), storeAccess, keyManager, accessCode)
    private val reconciler = OutcomeSyncReconciler(storeAccess, syncRepository, keyManager, accessCode)

    @Test
    fun `sin codigo de acceso no sube nada y lo pendiente sube despues`() = runTest {
        // Hallazgo A′ (v0.3.2): ni la restauración ni la reconciliación suben
        // antes de que exista el código de acceso.
        accessCode.codeExists = false
        database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 1, label = "pendiente", tags = "[]"))

        reconciler.restore()
        reconciler.reconcile()

        assertEquals(0, remote.uploaded.size)
        assertEquals(1, database.outcomeDao().pendingSync(4).size)

        accessCode.codeExists = true
        reconciler.reconcile()

        assertEquals(1, remote.uploaded.size)
        assertEquals(0, database.outcomeDao().pendingSync(4).size)
    }

    @Test
    fun `si no se puede leer el codigo de acceso no se sube`() = runTest {
        // Ante la duda se espera: subir es lo irreversible.
        accessCode.failOnRead = true
        database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 1, label = "pendiente", tags = "[]"))

        reconciler.restore()
        reconciler.reconcile()

        assertEquals(0, remote.uploaded.size)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `re-sube solo los guardados con constancia desfasada`() = runTest {
        database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 1, label = "pendiente", tags = "[]"))
        database.outcomeDao()
            .insert(OutcomeRow(savedAt = 200, rank = 2, label = "al dia", tags = "[]", syncedGeneration = 4))

        reconciler.restore()

        assertEquals(1, remote.uploaded.size)
        assertEquals(0, database.outcomeDao().pendingSync(4).size)
    }

    @Test
    fun `en un dispositivo nuevo repone desde la nube antes de subir`() = runTest {
        // Semilla remota: un guardado subido por «otro dispositivo».
        val seededId = database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 2, label = "remoto", tags = "[]"))
        syncRepository.push(seededId)
        database.outcomeDao().deleteById(seededId)

        reconciler.restore()

        val rows = database.outcomeDao().outcomes()
        assertEquals(1, rows.size)
        assertEquals("remoto", rows[0].label)
        assertEquals(seededId.toString(), rows[0].docId)
    }
}
