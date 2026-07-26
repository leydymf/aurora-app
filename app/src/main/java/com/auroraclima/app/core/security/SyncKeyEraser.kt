package com.auroraclima.app.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de core/security a la eliminación total (RF-20): destruye las llaves
 * con las que se sellaron los payloads de sincronización — la histórica atada
 * al dispositivo (`local_sync_key`, blobs v1 de instalaciones previas) y la
 * copia local de la DEK del sobre doble (RF-39). Cripto-borrado: los blobs que
 * pudieran quedar en cualquier caché dejan de ser descifrables desde aquí.
 */
@Singleton
class SyncKeyEraser @Inject constructor(
    private val legacyPayloadCipher: KeystorePayloadCipher,
    private val dekStore: DekStore
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) {
            legacyPayloadCipher.destroyKey()
            dekStore.erase()
        }
    }
}
