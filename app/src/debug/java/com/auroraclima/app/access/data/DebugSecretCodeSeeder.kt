package com.auroraclima.app.access.data

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.core.startup.AppInitializer
import javax.inject.Inject

/**
 * Siembra provisional de códigos SOLO para builds debug: permite probar RF-05 y
 * RF-12 antes de que exista la creación real del código en el registro.
 *
 * TODO(RF-08): eliminar esta clase (y su módulo) cuando el flujo de registro cree el
 * código de la usuaria, y definir con la directora el mecanismo real de distribución
 * del código de activación (hoy: «luna2026», el valor de la demo F2 del A2-6).
 */
class DebugSecretCodeSeeder @Inject constructor(
    private val repository: SecretCodeRepository,
    private val hasher: SecretCodeHasher
) : AppInitializer {
    override suspend fun initialize() {
        seedIfAbsent(CodeSlot.ACTIVATION, ACTIVATION_CODE)
        if (SEED_USER_CODE) {
            seedIfAbsent(CodeSlot.USER, USER_CODE)
        }
    }

    private suspend fun seedIfAbsent(slot: CodeSlot, code: String) {
        if (repository.storedCode(slot) != null) return
        val salt = hasher.newSalt()
        repository.saveCode(slot, StoredCodeHash(salt = salt, hash = hasher.hash(salt, code)))
    }

    companion object {
        /** Código de activación de prueba (decisión F2, alta-fidelidad-07). */
        private const val ACTIVATION_CODE = "luna2026"

        /**
         * Cambiar a true (y reinstalar o borrar datos) para probar RF-12 con un código
         * de usuaria; mientras exista código de usuaria, la activación deja de abrir.
         */
        private const val SEED_USER_CODE = false
        private const val USER_CODE = "cielo2026"
    }
}
