package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.MediaCipher
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.xor

/**
 * Cifrador de stream falso y reversible para las pruebas de la JVM (mismo
 * criterio que `ReversiblePayloadCipher`): el Keystore real no existe en la
 * JVM del host, así que `KeystoreMediaCipher` se prueba instrumentado. El XOR
 * byte a byte garantiza que lo escrito en disco difiere del claro (las
 * aserciones de ilegibilidad tienen sentido) y que la lectura restaura el
 * contenido exacto.
 */
class ReversibleMediaCipher : MediaCipher {
    override fun sealing(target: OutputStream): OutputStream = object : FilterOutputStream(target) {
        override fun write(b: Int) {
            out.write(b xor MASK_INT)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val masked = ByteArray(len) { index -> b[off + index] xor MASK }
            out.write(masked)
        }
    }

    override fun opening(source: InputStream): InputStream = object : FilterInputStream(source) {
        override fun read(): Int {
            val value = super.read()
            return if (value < 0) value else value xor MASK_INT
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            for (index in 0 until count) {
                b[off + index] = b[off + index] xor MASK
            }
            return count
        }
    }

    private companion object {
        const val MASK: Byte = 0x5A
        const val MASK_INT: Int = 0x5A
    }
}
