package com.auroraclima.app.access.data

import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.VisibleForTesting
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * HMAC-SHA256 sobre (salt ‖ código) con una llave no exportable generada en el
 * Android Keystore: sin la llave del dispositivo el hash almacenado no permite
 * ataques de diccionario fuera de línea (RNF-01, regla 5 de CLAUDE.md).
 *
 * Cada cálculo habla con el servicio del Keystore y puede tardar; en el hilo principal
 * congeló la app en API 30 (ANR, hallazgo de la auditora en v0.3.4). Por eso corre en
 * un carril de fondo de un solo hilo: los cálculos van en fila y uno cuyo llamador se
 * canceló mientras esperaba turno no llega a empezar, así que el tecleo rápido no los
 * acumula. Si la llamada se cancela con el cálculo ya en marcha, este termina en el
 * carril pero su resultado se descarta (`withContext`).
 */
@Singleton
class KeystoreSecretCodeHasher @Inject constructor() : SecretCodeHasher {
    override fun newSalt(): ByteArray = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

    override suspend fun hash(salt: ByteArray, code: String): ByteArray = withContext(lane) { computeHmac(salt, code) }

    /**
     * El cálculo bloqueante. La guarda hace que un llamado en el hilo principal falle
     * en vez de congelar la app: la puerta degrada el fallo a una búsqueda normal y la
     * creación a un error genérico, así que nunca abre de más (§3 de ARQUITECTURA.md).
     */
    @VisibleForTesting
    internal fun computeHmac(salt: ByteArray, code: String): ByteArray {
        check(Looper.getMainLooper()?.isCurrentThread != true) { "fuera del carril" }
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(obtainKey())
        mac.update(salt)
        return mac.doFinal(code.toByteArray(Charsets.UTF_8))
    }

    /**
     * Eliminación total (RF-20): sin esta llave, cualquier hash que sobreviviera
     * es incomprobable — ningún código volverá a coincidir con él.
     *
     * Va por el mismo carril que los cálculos: un HMAC que siguiera en marcha no puede
     * volver a crear la llave justo después del borrado (`obtainKey` la genera si falta).
     */
    suspend fun destroyKey() = withContext(lane) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    @Synchronized
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Alias neutro: los alias del keystore son visibles para herramientas de
        // inspección del propio uid (RNF-05).
        private const val KEY_ALIAS = "local_index_key"
        private const val SALT_SIZE_BYTES = 16

        // Vista de un hilo sobre el despachador de E/S: no crea hilos propios.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val lane: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
