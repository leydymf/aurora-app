package com.auroraclima.app.access.domain.repository

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash

/**
 * Almacenamiento de los hashes de los códigos de acceso (RF-05, RF-12).
 * Solo se guardan hash y salt; nunca el código en claro.
 *
 * TODO(RF-08): la escritura del código de usuaria llegará con la pantalla de creación
 * de código (día 3); hoy solo la usa la siembra provisional de builds debug.
 */
interface SecretCodeRepository {
    /** Hash almacenado para la ranura, o null si aún no existe. */
    suspend fun storedCode(slot: CodeSlot): StoredCodeHash?

    /** Guarda (o reemplaza) el hash de una ranura. */
    suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash)
}
