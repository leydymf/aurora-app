package com.auroraclima.app.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de core/security a la eliminación total (RF-20): destruye la llave con
 * la que se sellaron los payloads de sincronización. Cripto-borrado — los blobs
 * que pudieran quedar en cualquier caché dejan de ser descifrables.
 */
@Singleton
class SyncKeyEraser @Inject constructor(
    private val payloadCipher: KeystorePayloadCipher
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) { payloadCipher.destroyKey() }
    }
}
