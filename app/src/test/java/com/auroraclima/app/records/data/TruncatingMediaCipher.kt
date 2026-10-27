package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.MediaCipher
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Cifrador falso que imita el defecto del Keystore de Android 8.0 (v0.3.2): el
 * sellado pierde bytes sin avisar y la apertura no lanza, solo entrega menos.
 * Sirve para probar que el almacén lo detecta y no guarda nada truncado.
 */
class TruncatingMediaCipher(private val keptBytes: Int = 16) : MediaCipher {
    private val reversible = ReversibleMediaCipher()

    override fun sealing(target: OutputStream): OutputStream = object : FilterOutputStream(reversible.sealing(target)) {
        private var written = 0

        override fun write(b: Int) {
            if (written < keptBytes) out.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val kept = (keptBytes - written).coerceIn(0, len)
            if (kept > 0) out.write(b, off, kept)
            written += len
        }
    }

    override fun opening(source: InputStream): InputStream = reversible.opening(source)
}
