package com.auroraclima.app.access.data

import android.content.Context
import android.content.SharedPreferences
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Marca de la guía sellada con la llave de datos de la cuenta (el mismo
 * [PayloadCipher] que cifra los registros), nunca en texto plano: ni el valor
 * ni su significado se leen del archivo. El archivo y las claves tienen nombres
 * neutros, como los demás de la casa (RNF-05).
 *
 * Usa el [PayloadCipher] de la casa, cuya llave es la DEK de la cuenta (RF-39):
 * la marca queda atada a ella a propósito. Sin DEK (cuenta nueva sin llave) o
 * con otra (respaldo reiniciado) la marca no abre y la guía simplemente no
 * aparece; es el fallo aceptable, nunca un error visible.
 *
 * Consumir borra la marca ANTES de abrirla: si el proceso muere en medio, lo que
 * señalaba se pierde en lugar de repetirse (ocurre una sola vez, a lo sumo). Si
 * ni el borrado se confirma en disco, la marca podría reaparecer; es de mejor
 * esfuerzo, como el resto de los borrados. El borrado total de la cuenta lo
 * hace [WalkthroughEraser]. Marcar y consumir se hacen de a uno ([lock]): la guía
 * arma la marca al llegar a la nube y la desarma si se va de ese paso, y un
 * desarme rápido no debe adelantarse al armado.
 *
 * Es el archivo del recorrido de v0.3.3 y del aviso de v0.3.2 (mismas claves): el
 * valor de entonces, `[0] = 1`, es [WalkthroughMark.FROM_START], que es lo que
 * significaba (esa persona aún no había llegado a Inicio).
 */
@Singleton
class SealedWalkthroughRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: PayloadCipher
) : WalkthroughRepository {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val lock = Mutex()

    override suspend fun mark(mark: WalkthroughMark): Result<Unit> = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val sealed = cipher.seal(valueOf(mark))
                val written = preferences.edit()
                    .putString(IV_KEY, encode(sealed.iv))
                    .putString(BLOB_KEY, encode(sealed.ciphertext))
                    .commit()
                if (!written) throw IOException()
            }
        }
    }

    override suspend fun consume(): Result<WalkthroughMark?> = lock.withLock {
        withContext(Dispatchers.IO) {
            val stored = runCatching {
                preferences.getString(IV_KEY, null) to preferences.getString(BLOB_KEY, null)
            }.getOrNull()
            // Se consume primero y fuera del runCatching (que tragaría una cancelación):
            // pase lo que pase al abrirla, no vuelve a leerse.
            clearNow()
            val (iv, blob) = stored ?: return@withContext Result.success(null)
            if (iv == null || blob == null) return@withContext Result.success(null)
            // Si no abre (otra cuenta, dato adulterado) es Result.failure: para el
            // llamador, ninguna marca.
            runCatching {
                markOf(cipher.open(SealedPayload(ciphertext = decode(blob), iv = decode(iv))))
            }
        }
    }

    /** Eliminación total (RF-20): deja el archivo vacío, como los demás. */
    suspend fun clear() {
        lock.withLock { withContext(Dispatchers.IO) { clearNow() } }
    }

    private fun clearNow() {
        runCatching { preferences.edit().clear().commit() }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        // Nombres neutros, plausibles en una app de clima (RNF-05).
        const val PREFS_NAME = "view_meta"
        const val IV_KEY = "v0"
        const val BLOB_KEY = "v1"

        // Valor + relleno: el texto cifrado mide siempre lo mismo y GCM autentica
        // también los ceros. El primer byte dice cuál marca es.
        const val MARK_SIZE = 16
        const val FROM_START_BYTE: Byte = 1
        const val TO_FINISH_BYTE: Byte = 2

        fun valueOf(mark: WalkthroughMark): ByteArray = ByteArray(MARK_SIZE).also {
            it[0] = when (mark) {
                WalkthroughMark.FROM_START -> FROM_START_BYTE
                WalkthroughMark.TO_FINISH -> TO_FINISH_BYTE
            }
        }

        /** Solo un valor exacto cuenta: cualquier otro contenido es ninguna marca. */
        fun markOf(bytes: ByteArray): WalkthroughMark? =
            WalkthroughMark.entries.firstOrNull { valueOf(it).contentEquals(bytes) }
    }
}
