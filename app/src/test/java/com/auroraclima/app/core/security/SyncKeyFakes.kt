package com.auroraclima.app.core.security

import java.security.MessageDigest

/** Fakes compartidos por las pruebas del esquema de llaves (RF-39). */

/**
 * Derivación instantánea y determinista (SHA-256 de contraseña|salt|iteraciones):
 * conserva las propiedades que la máquina de estados necesita — sensibilidad a
 * cada parámetro y determinismo — sin pagar PBKDF2. Cuenta las llamadas para
 * poder afirmar que la vía rápida NO deriva.
 */
internal class CountingKeyDerivation : PasswordKeyDerivation {
    var calls = 0
        private set

    override fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        calls++
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(password.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(iterations.toString().toByteArray(Charsets.UTF_8))
        return digest.digest()
    }
}

internal class InMemoryDekStore : DekStore {
    private val entries = mutableMapOf<String, DekStore.StoredDek>()

    var writeCalls = 0
        private set

    override fun read(uid: String): DekStore.StoredDek? = entries[uid]

    override fun write(uid: String, dek: ByteArray, generation: Long, revision: String) {
        writeCalls++
        // Una sola cuenta por instalación: escribir reemplaza cualquier dueña anterior.
        entries.clear()
        entries[uid] = DekStore.StoredDek(dek.copyOf(), generation, revision)
    }

    override fun erase() {
        entries.clear()
    }
}

internal class FakeKeyEnvelopeRemoteSource : KeyEnvelopeRemoteSource {
    var stored: PublishedKeyEnvelope? = null
    var failFetch = false
    var failPublish = false
    var deleteCalls = 0
        private set

    val publishLog = mutableListOf<PublishedKeyEnvelope>()

    override suspend fun fetch(): PublishedKeyEnvelope? {
        if (failFetch) throw IllegalStateException()
        return stored
    }

    override suspend fun publish(envelope: PublishedKeyEnvelope) {
        if (failPublish) throw IllegalStateException()
        publishLog += envelope
        stored = envelope
    }

    override suspend fun delete() {
        deleteCalls++
        stored = null
    }
}

internal class RecordingRemoteEraser : RemoteAccountDataEraser {
    var result: Result<Unit> = Result.success(Unit)
    var calls = 0
        private set

    override suspend fun erase(): Result<Unit> {
        calls++
        return result
    }
}

/** Gestor falso para probar consumidores de la DEK (cifrador, casos de uso). */
internal class FakeSyncKeyManager(
    var dek: ByteArray? = null,
    var generation: Long? = null
) : SyncKeyManager {
    var establishOutcome: Result<KeyEstablishment> = Result.success(KeyEstablishment.Ready)
    var rewrapOutcome: Result<Unit> = Result.success(Unit)
    var restartNotice = false

    val establishedPasswords = mutableListOf<String>()
    val rewrappedPasswords = mutableListOf<String>()

    override fun currentDek(): ByteArray? = dek

    override fun currentGeneration(): Long? = generation

    override suspend fun establish(password: String): Result<KeyEstablishment> {
        establishedPasswords += password
        return establishOutcome
    }

    override suspend fun rewrap(newPassword: String): Result<Unit> {
        rewrappedPasswords += newPassword
        return rewrapOutcome
    }

    override fun consumeRestartNotice(): Boolean {
        val value = restartNotice
        restartNotice = false
        return value
    }
}
