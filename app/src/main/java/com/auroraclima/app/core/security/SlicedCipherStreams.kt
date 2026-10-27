package com.auroraclima.app.core.security

import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher

/**
 * Tope de bytes por llamada al cifrador del Keystore (v0.3.2). En Android 8.0 y
 * 8.1, si una sola llamada a `update()` o `doFinal()` le entrega más de unos
 * 128 KB, el Keystore devuelve solo la salida del último trozo interno y no lanza
 * error. 32 KB queda muy por debajo; medido en API 26, 27 y 30.
 */
internal const val KEYSTORE_SLICE_BYTES = 32 * 1024

/**
 * Envuelve el stream cifrante para que el cifrador reciba trozos de a lo sumo
 * [sliceBytes]: se reparte cada escritura grande y se juntan las pequeñas (8 KB al
 * copiar, 4 KB al comprimir una imagen) para llamar al Keystore menos veces.
 */
internal fun slicedSealing(cipherOutput: OutputStream, sliceBytes: Int = KEYSTORE_SLICE_BYTES): OutputStream =
    SlicedOutputStream(BufferedOutputStream(cipherOutput, sliceBytes), sliceBytes)

/**
 * Reparte cada escritura en trozos de [sliceBytes] antes de que lleguen al búfer:
 * una escritura del tamaño del búfer o mayor lo atravesaría entera.
 */
internal class SlicedOutputStream(target: OutputStream, private val sliceBytes: Int) : FilterOutputStream(target) {
    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        val end = off + len
        while (offset < end) {
            val count = minOf(sliceBytes, end - offset)
            out.write(b, offset, count)
            offset += count
        }
    }
}

/**
 * Descifra leyendo [source] en trozos de [sliceBytes]. Reemplaza a
 * `CipherInputStream`, que entrega al cifrador 512 bytes por llamada (muy lento
 * con el Keystore en gama baja). Un sellado alterado o truncado lanza
 * [IOException] al llegar al final, igual que antes.
 */
internal class SlicedCipherInputStream(
    private val source: InputStream,
    private val cipher: Cipher,
    sliceBytes: Int = KEYSTORE_SLICE_BYTES
) : InputStream() {
    private val input = ByteArray(sliceBytes)
    private val single = ByteArray(1)
    private var pending = ByteArray(0)
    private var position = 0
    private var finished = false

    override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and BYTE_MASK

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position == pending.size) {
            if (finished) return -1
            refill()
        }
        val count = minOf(len, pending.size - position)
        System.arraycopy(pending, position, b, off, count)
        position += count
        return count
    }

    override fun available(): Int = pending.size - position

    /**
     * Cierra la fuente y, si no se leyó hasta el final, termina la operación del
     * cifrador (como `CipherInputStream`) para que el Keystore no la retenga. El
     * resultado de ese cierre anticipado se descarta.
     */
    override fun close() {
        try {
            source.close()
        } finally {
            pending.fill(0)
            if (!finished) {
                finished = true
                try {
                    cipher.doFinal().fill(0)
                } catch (ignored: GeneralSecurityException) {
                    // Sin leer hasta el final el sello no se comprueba: no hay nada que entregar.
                }
            }
        }
    }

    private fun refill() {
        val count = source.read(input)
        pending = try {
            if (count < 0) {
                finished = true
                cipher.doFinal()
            } else {
                cipher.update(input, 0, count) ?: ByteArray(0)
            }
        } catch (error: GeneralSecurityException) {
            throw IOException(error)
        }
        position = 0
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
