package com.auroraclima.app.access.data

import android.content.Context
import android.content.SharedPreferences
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda hash y salt de los códigos en SharedPreferences privadas. El contenido es
 * ilegible sin la llave del Keystore (solo hashes HMAC) y los nombres de archivo y
 * claves son neutros: no delatan nada si se inspecciona el almacenamiento (RNF-05).
 * `allowBackup=false` impide que salgan del dispositivo (pendiente P-00 resuelto).
 */
@Singleton
class LocalSecretCodeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SecretCodeRepository {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun storedCode(slot: CodeSlot): StoredCodeHash? = withContext(Dispatchers.IO) {
        val salt = preferences.getString(saltKey(slot), null) ?: return@withContext null
        val hash = preferences.getString(hashKey(slot), null) ?: return@withContext null
        // Dato corrupto = código inexistente: el fallo nunca cruza hacia la fachada (§3).
        runCatching { StoredCodeHash(salt = decode(salt), hash = decode(hash)) }.getOrNull()
    }

    override suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(saltKey(slot), encode(value.salt))
                .putString(hashKey(slot), encode(value.hash))
                .commit()
        }
    }

    private fun saltKey(slot: CodeSlot) = "${prefix(slot)}_s"

    private fun hashKey(slot: CodeSlot) = "${prefix(slot)}_h"

    private fun prefix(slot: CodeSlot) = when (slot) {
        CodeSlot.ACTIVATION -> "i0"
        CodeSlot.USER -> "i1"
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        // Nombre neutro, plausible en una app de clima (RNF-05).
        private const val PREFS_NAME = "search_index"
    }
}
