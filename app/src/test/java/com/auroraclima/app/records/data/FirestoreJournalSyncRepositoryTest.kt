package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RF-37: sincronización cifrada. Verifica que (a) el contenido nunca viaje en
 * claro en el DTO, (b) sin conexión la escritura se encole y suba al reconectar
 * (la cola durable del SDK, RF-38), (c) el docId sea el id local y (d) el borrado
 * remoto apunte a ese mismo id. Bajo Robolectric por el uso de `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class FirestoreJournalSyncRepositoryTest {
    private val remote = FakeJournalSyncRemoteSource()
    private val cipher = ReversiblePayloadCipher()
    private val repository = FirestoreJournalSyncRepository(remote, cipher)

    private val entry = JournalEntry(
        id = 7,
        occurredOn = LocalDate.of(2026, 6, 28),
        kind = EntryKind.ECONOMIC,
        detail = "tomó el dinero del mercado"
    )

    @Test
    fun `el payload nunca viaja en claro y el docId es el id local`() = runTest {
        val result = repository.push(entry)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.uploaded.size)
        val (docId, dto) = remote.uploaded[0]
        // docId determinista = id local (RF-37).
        assertEquals("7", docId)

        // El texto sensible no aparece en NINGÚN campo del DTO.
        val cleartext = "tomó el dinero del mercado"
        assertFalse(dto.ciphertext.contains(cleartext))
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains(cleartext))
        assertEquals(1L, dto.schemaVersion)

        // Round-trip: descifrando el payload se recupera el contenido real.
        val opened = cipher.open(
            SealedPayload(
                ciphertext = Base64.getDecoder().decode(dto.ciphertext),
                iv = Base64.getDecoder().decode(dto.iv)
            )
        )
        val json = JSONObject(opened.toString(Charsets.UTF_8))
        assertEquals(entry.occurredOn.toEpochDay(), json.getLong("day"))
        assertEquals(EntryKind.ECONOMIC.storageCode, json.getString("kind"))
        assertEquals(cleartext, json.getString("detail"))
    }

    @Test
    fun `sin conexión encola y al reconectar sube`() = runTest {
        remote.online = false

        val result = repository.push(entry)

        // La escritura no se pierde: queda en cola, sin error visible (§3).
        assertTrue(result.isSuccess)
        assertTrue(remote.uploaded.isEmpty())
        assertEquals(1, remote.queued.size)

        remote.reconnect()
        assertEquals(1, remote.uploaded.size)
        assertEquals("7", remote.uploaded[0].first)
    }

    @Test
    fun `remove borra el documento del id local`() = runTest {
        val result = repository.remove(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf("7"), remote.deleted)
    }

    @Test
    fun `la edicion re-cifra y sobreescribe el mismo documento`() = runTest {
        // RF-35 criterio 1: el cambio se refleja en la sync — mismo docId, payload nuevo.
        repository.push(entry)
        val edited = entry.copy(detail = "el relato corregido")

        val result = repository.push(edited)

        assertTrue(result.isSuccess)
        assertEquals(2, remote.uploaded.size)
        assertEquals(remote.uploaded[0].first, remote.uploaded[1].first)
        val dto = remote.uploaded[1].second
        // El contenido editado tampoco viaja en claro…
        assertFalse(Base64.getDecoder().decode(dto.ciphertext).toString(Charsets.UTF_8).contains("corregido"))
        // …pero descifrando el payload nuevo se recupera el texto editado.
        val opened = cipher.open(
            SealedPayload(
                ciphertext = Base64.getDecoder().decode(dto.ciphertext),
                iv = Base64.getDecoder().decode(dto.iv)
            )
        )
        assertEquals("el relato corregido", JSONObject(opened.toString(Charsets.UTF_8)).getString("detail"))
    }
}
