package com.auroraclima.app.access.data

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.core.startup.AppInitializer
import javax.inject.Inject

/**
 * Siembra del código de ACTIVACIÓN, solo en builds debug: sigue siendo la única
 * vía de activación del prototipo (RF-05, P-20) mientras la directora valida el
 * mecanismo real de distribución. El código de USUARIA ya no se siembra: lo crea
 * la pantalla de RF-08 dentro del flujo de registro.
 */
class DebugSecretCodeSeeder @Inject constructor(
    private val repository: SecretCodeRepository,
    private val hasher: SecretCodeHasher
) : AppInitializer {
    override suspend fun initialize() {
        if (repository.storedCode(CodeSlot.ACTIVATION) != null) return
        val salt = hasher.newSalt()
        repository.saveCode(
            CodeSlot.ACTIVATION,
            StoredCodeHash(salt = salt, hash = hasher.hash(salt, ACTIVATION_CODE))
        )
    }

    companion object {
        /** Código de activación de prueba (decisión F2, alta-fidelidad-07). */
        private const val ACTIVATION_CODE = "luna2026"
    }
}
