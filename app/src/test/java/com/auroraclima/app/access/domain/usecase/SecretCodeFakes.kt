package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.CityNameDirectory
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred

/** Fakes compartidos por los tests de los casos de uso del código de acceso. */

internal suspend fun FakeSecretCodeRepository.seed(slot: CodeSlot, code: String, hasher: SecretCodeHasher) {
    val salt = hasher.newSalt()
    saveCode(slot, StoredCodeHash(salt = salt, hash = hasher.hash(salt, code)))
}

/**
 * Hash determinista de prueba: SHA-256(salt ‖ código), sin Keystore.
 *
 * Con [hold] armado, cada cálculo queda suspendido hasta que se complete: simula el
 * Keystore lento (el ANR de API 30) para probar cancelaciones durante un cálculo.
 */
internal class FakeSecretCodeHasher : SecretCodeHasher {
    var hashCallCount = 0
    var failOnHash = false
    var hold: CompletableDeferred<Unit>? = null

    /** Cálculos que empezaron y todavía no terminan ni fueron cancelados. */
    var inFlight = 0
        private set

    override fun newSalt(): ByteArray = ByteArray(16) { 7 }

    override suspend fun hash(salt: ByteArray, code: String): ByteArray {
        if (failOnHash) throw IllegalStateException("llave invalidada")
        hashCallCount++
        inFlight++
        try {
            hold?.await()
        } finally {
            inFlight--
        }
        return MessageDigest.getInstance("SHA-256").digest(salt + code.toByteArray(Charsets.UTF_8))
    }
}

internal class FakeSecretCodeRepository : SecretCodeRepository {
    var failOnRead = false
    var failOnWrite = false

    private val stored = mutableMapOf<CodeSlot, StoredCodeHash>()

    override suspend fun storedCode(slot: CodeSlot): StoredCodeHash? {
        if (failOnRead) throw IllegalStateException("almacenamiento corrupto")
        return stored[slot]
    }

    override suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash) {
        if (failOnWrite) throw IllegalStateException("almacenamiento no disponible")
        stored[slot] = value
    }
}

internal class FakeCityNameDirectory(vararg names: String) : CityNameDirectory {
    var failOnRead = false

    private val cities = names.toSet()

    override suspend fun isRealCity(name: String): Boolean {
        if (failOnRead) throw IllegalStateException("recurso corrupto")
        return name.trim().lowercase() in cities
    }
}
