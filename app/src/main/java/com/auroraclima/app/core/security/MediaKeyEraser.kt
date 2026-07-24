package com.auroraclima.app.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de core/security a la eliminación total (RF-20): destruye la llave con
 * la que se cifró el almacén de adjuntos. Cripto-borrado — cualquier archivo
 * que sobreviva al borrado del directorio deja de ser descifrable. Mismo patrón
 * que [SyncKeyEraser].
 */
@Singleton
class MediaKeyEraser @Inject constructor(
    private val mediaCipher: KeystoreMediaCipher
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) { mediaCipher.destroyKey() }
    }
}
