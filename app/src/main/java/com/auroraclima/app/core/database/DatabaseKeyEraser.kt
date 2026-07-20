package com.auroraclima.app.core.database

import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de core/database a la eliminación total (RF-20): la passphrase de la BD
 * cifrada deja de existir (llave del Keystore y blob envuelto). El archivo de la
 * BD lo borra su dueño (records); aunque ese paso fallara, sin esta passphrase
 * el archivo es ilegible.
 */
@Singleton
class DatabaseKeyEraser @Inject constructor(
    private val keyManager: DatabaseKeyManager
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) { keyManager.erase() }
    }
}
