package com.auroraclima.app.access.data

import android.content.Context
import android.content.SharedPreferences
import com.auroraclima.app.access.domain.repository.HomeTourRepository
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marca «recorrido pendiente» sellada con la llave de datos de la cuenta (el mismo
 * [PayloadCipher] que cifra los registros), nunca en texto plano: ni el valor
 * ni su significado se leen del archivo. El archivo y las claves tienen nombres
 * neutros, como los demás de la casa (RNF-05).
 *
 * Usa el [PayloadCipher] de la casa, cuya llave es la DEK de la cuenta (RF-39):
 * la marca queda atada a ella a propósito. Sin DEK (cuenta nueva sin llave) o
 * con otra (respaldo reiniciado) la marca no abre y el recorrido simplemente no
 * aparece; es el fallo aceptable, nunca un error visible.
 *
 * Consumir borra la marca ANTES de abrirla: si el proceso muere en medio, el
 * recorrido se pierde en lugar de repetirse (aparece una sola vez, a lo sumo). Si
 * ni el borrado se confirma en disco, la marca podría reaparecer; es de mejor
 * esfuerzo, como el resto de los borrados. El borrado total de la cuenta lo
 * hace [HomeTourEraser].
 *
 * Es el mecanismo del aviso de bienvenida de v0.3.2, que el recorrido reemplaza
 * (v0.3.3): mismo archivo, mismas claves y misma marca. Una marca de aviso que
 * hubiera quedado de v0.3.2 se lee como recorrido pendiente, que es lo que
 * significa: esa persona aún no había llegado a Inicio.
 */
@Singleton
class SealedHomeTourRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: PayloadCipher
) : HomeTourRepository {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun markPending(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sealed = cipher.seal(PENDING_MARK)
            val written = preferences.edit()
                .putString(IV_KEY, encode(sealed.iv))
                .putString(BLOB_KEY, encode(sealed.ciphertext))
                .commit()
            if (!written) throw IOException()
        }
    }

    override suspend fun consumePending(): Result<Boolean> = withContext(Dispatchers.IO) {
        val stored = runCatching {
            preferences.getString(IV_KEY, null) to preferences.getString(BLOB_KEY, null)
        }.getOrNull()
        // Se consume primero y fuera del runCatching (que tragaría una cancelación):
        // pase lo que pase al abrirla, no vuelve a leerse.
        clear()
        val (iv, blob) = stored ?: return@withContext Result.success(false)
        if (iv == null || blob == null) return@withContext Result.success(false)
        // Si no abre (otra cuenta, dato adulterado) es Result.failure: para el
        // llamador, «no pendiente».
        runCatching {
            cipher.open(SealedPayload(ciphertext = decode(blob), iv = decode(iv))).contentEquals(PENDING_MARK)
        }
    }

    /** Eliminación total (RF-20) y consumo: deja el archivo vacío, como los demás. */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { preferences.edit().clear().commit() }
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        // Nombres neutros, plausibles en una app de clima (RNF-05).
        const val PREFS_NAME = "view_meta"
        const val IV_KEY = "v0"
        const val BLOB_KEY = "v1"

        // Versión + relleno: el texto cifrado mide siempre lo mismo y GCM
        // autentica también los ceros.
        val PENDING_MARK = ByteArray(16).also { it[0] = 1 }
    }
}
