package com.auroraclima.app.access.data

import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de access a la eliminación total (RF-20): la puerta deja de existir.
 * Primero la llave del Keystore (cripto-borrado: un hash que sobreviva es
 * incomprobable) y luego los hashes de ambas ranuras — desde entonces la clave
 * de la usuaria responde como cualquier búsqueda fallida de ciudad (RF-12/13).
 */
@Singleton
class AccessCredentialsEraser @Inject constructor(
    private val hasher: KeystoreSecretCodeHasher,
    private val repository: LocalSecretCodeRepository
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) {
            // Mejor esfuerzo por paso: un Keystore que falle no debe impedir
            // borrar los hashes, ni al revés (contrato del eraser).
            runCatching { hasher.destroyKey() }
            runCatching { repository.clearAll() }
        }
    }
}
