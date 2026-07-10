package com.auroraclima.app.access.domain.repository

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash

/**
 * Almacenamiento de los hashes de los códigos de acceso (RF-05, RF-12).
 * Solo se guardan hash y salt; nunca el código en claro. La ranura USER la
 * escribe [CreateSecretCodeUseCase] (RF-08); la ACTIVATION, la siembra de
 * builds debug (única vía de activación del prototipo, P-20).
 */
interface SecretCodeRepository {
    /** Hash almacenado para la ranura, o null si aún no existe. */
    suspend fun storedCode(slot: CodeSlot): StoredCodeHash?

    /** Guarda (o reemplaza) el hash de una ranura. */
    suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash)
}
