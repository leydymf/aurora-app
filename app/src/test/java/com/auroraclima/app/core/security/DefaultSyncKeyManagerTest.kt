package com.auroraclima.app.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Máquina de estados del «sobre doble» (RF-39): primer arranque, vía rápida sin
 * KDF, restauración en dispositivo nuevo, auto-reparación, reinicio del
 * respaldo por contraseña restablecida y re-envoltura por cambio de contraseña.
 * Cripto real de sobre (AES-GCM) con derivación instantánea contada.
 */
class DefaultSyncKeyManagerTest {
    private val derivation = CountingKeyDerivation()
    private val wrapper = PasswordKeyWrapper(derivation, iterations = 10)
    private val remote = FakeKeyEnvelopeRemoteSource()
    private val dekStore = InMemoryDekStore()
    private val eraser = RecordingRemoteEraser()
    private var uid: String? = "uid-a"
    private val manager = DefaultSyncKeyManager(
        identity = { uid },
        remote = remote,
        dekStore = dekStore,
        keyWrapper = wrapper,
        remoteErasers = setOf(eraser)
    )

    @Test
    fun `primer arranque publica el sobre y guarda la copia local`() = runTest {
        val outcome = manager.establish("brisa-serena-04")

        assertEquals(KeyEstablishment.Ready, outcome.getOrThrow())
        val published = remote.stored ?: error("sin sobre publicado")
        assertEquals(1L, published.generation)
        // El sobre publicado abre con la contraseña y es la misma DEK local.
        assertArrayEquals(manager.currentDek(), wrapper.unwrap(published.envelope, "brisa-serena-04"))
        assertEquals(1L, manager.currentGeneration())
    }

    @Test
    fun `sobre al dia no deriva nada ni publica`() = runTest {
        manager.establish("brisa-serena-04").getOrThrow()
        val derivationsAfterSetup = derivation.calls
        val publishesAfterSetup = remote.publishLog.size

        val outcome = manager.establish("brisa-serena-04")

        assertEquals(KeyEstablishment.Ready, outcome.getOrThrow())
        // Vía rápida del login corriente: cero KDF, cero escrituras.
        assertEquals(derivationsAfterSetup, derivation.calls)
        assertEquals(publishesAfterSetup, remote.publishLog.size)
    }

    @Test
    fun `dispositivo nuevo adopta la DEK del sobre publicado`() = runTest {
        // El «dispositivo A» publica…
        manager.establish("brisa-serena-04").getOrThrow()
        val originalDek = manager.currentDek() ?: error("sin DEK")
        // …y el «dispositivo B» (sin copia local) inicia sesión.
        dekStore.erase()

        val outcome = manager.establish("brisa-serena-04")

        assertEquals(KeyEstablishment.Ready, outcome.getOrThrow())
        assertArrayEquals(originalDek, manager.currentDek())
        assertEquals(remote.stored?.generation, manager.currentGeneration())
    }

    @Test
    fun `sobre publicado que no abre se auto-repara con la copia local`() = runTest {
        // Cambio de contraseña interrumpido: el sobre quedó envuelto con una
        // contraseña que nunca entró en vigor; la copia local sigue sana.
        manager.establish("brisa-serena-04").getOrThrow()
        val dek = manager.currentDek() ?: error("sin DEK")
        remote.stored = PublishedKeyEnvelope(wrapper.wrap(dek, "contrasena-fantasma"), 1, "otra-rev")

        val outcome = manager.establish("brisa-serena-04")

        assertEquals(KeyEstablishment.Ready, outcome.getOrThrow())
        val repaired = remote.stored ?: error("sin sobre")
        // Re-publicado con la contraseña vigente, misma generación (nada se re-sube)…
        assertArrayEquals(dek, wrapper.unwrap(repaired.envelope, "brisa-serena-04"))
        assertEquals(1L, repaired.generation)
        // …y la copia local queda alineada a la revisión nueva.
        assertEquals(repaired.revision, dekStore.read("uid-a")?.revision)
    }

    @Test
    fun `contrasena restablecida sin copia local reinicia el respaldo`() = runTest {
        remote.stored = PublishedKeyEnvelope(wrapper.wrap(ByteArray(32) { 7 }, "contrasena-olvidada"), 3, "r3")

        val outcome = manager.establish("contrasena-nueva")

        assertEquals(KeyEstablishment.Restarted, outcome.getOrThrow())
        // Purga de los blobs ilegibles (decisión de la sesión: sí purgar)…
        assertEquals(1, eraser.calls)
        // …sobre nuevo con generación siguiente (dispara la re-subida en el
        // dispositivo viejo si algún día vuelve a entrar)…
        val republished = remote.stored ?: error("sin sobre")
        assertEquals(4L, republished.generation)
        assertArrayEquals(manager.currentDek(), wrapper.unwrap(republished.envelope, "contrasena-nueva"))
        // …y el aviso se consume UNA sola vez.
        assertTrue(manager.consumeRestartNotice())
        assertFalse(manager.consumeRestartNotice())
    }

    @Test
    fun `si la purga falla el reinicio no avanza y se reintenta despues`() = runTest {
        val abandoned = PublishedKeyEnvelope(wrapper.wrap(ByteArray(32) { 7 }, "contrasena-olvidada"), 3, "r3")
        remote.stored = abandoned
        eraser.result = Result.failure(IllegalStateException())

        val outcome = manager.establish("contrasena-nueva")

        assertTrue(outcome.isFailure)
        // Nada avanzó: mismo sobre, sin copia local, sin aviso. El siguiente
        // login re-entra al mismo camino (idempotente).
        assertEquals(abandoned, remote.stored)
        assertNull(dekStore.read("uid-a"))
        assertFalse(manager.consumeRestartNotice())
    }

    @Test
    fun `si publicar falla en el primer arranque no queda estado a medias`() = runTest {
        remote.failPublish = true

        val outcome = manager.establish("brisa-serena-04")

        assertTrue(outcome.isFailure)
        assertNull(dekStore.read("uid-a"))
        assertNull(manager.currentDek())
    }

    @Test
    fun `un formato de sobre futuro no se toca`() = runTest {
        val future = PublishedKeyEnvelope(
            wrapper.wrap(ByteArray(32) { 7 }, "brisa-serena-04"),
            generation = 9,
            revision = "r9",
            schemaVersion = PublishedKeyEnvelope.CURRENT_SCHEMA_VERSION + 1
        )
        remote.stored = future

        val outcome = manager.establish("brisa-serena-04")

        assertTrue(outcome.isFailure)
        assertEquals(future, remote.stored)
        assertNull(dekStore.read("uid-a"))
    }

    @Test
    fun `sin sesion no hay establecimiento`() = runTest {
        uid = null

        assertTrue(manager.establish("brisa-serena-04").isFailure)
    }

    @Test
    fun `rewrap publica con la contrasena nueva sin tocar la copia local`() = runTest {
        manager.establish("brisa-serena-04").getOrThrow()
        val localBefore = dekStore.read("uid-a") ?: error("sin copia local")

        manager.rewrap("brisa-nueva-05").getOrThrow()

        val republished = remote.stored ?: error("sin sobre")
        // Misma DEK y generación (los registros no se re-cifran), revisión nueva…
        assertArrayEquals(localBefore.dek, wrapper.unwrap(republished.envelope, "brisa-nueva-05"))
        assertEquals(localBefore.generation, republished.generation)
        assertFalse(localBefore.revision == republished.revision)
        // …y la copia local NO avanza todavía (eso lo hace establish después).
        assertEquals(localBefore, dekStore.read("uid-a"))

        // El establish posterior con la contraseña ya activa adopta la revisión.
        manager.establish("brisa-nueva-05").getOrThrow()
        assertEquals(republished.revision, dekStore.read("uid-a")?.revision)
    }

    @Test
    fun `rewrap sin copia local falla sin publicar`() = runTest {
        assertTrue(manager.rewrap("brisa-nueva-05").isFailure)
        assertNull(remote.stored)
    }

    @Test
    fun `revision desalineada con la misma contrasena adopta sin reparar`() = runTest {
        // Cambio culminado desde este u otro aparato: el sobre abre con la
        // contraseña vigente y solo hay que alinear la copia local.
        manager.establish("brisa-serena-04").getOrThrow()
        val dek = manager.currentDek() ?: error("sin DEK")
        remote.stored = PublishedKeyEnvelope(wrapper.wrap(dek, "brisa-serena-04"), 1, "rev-remota")
        val publishesBefore = remote.publishLog.size

        manager.establish("brisa-serena-04").getOrThrow()

        assertEquals(publishesBefore, remote.publishLog.size)
        assertEquals("rev-remota", dekStore.read("uid-a")?.revision)
    }
}
