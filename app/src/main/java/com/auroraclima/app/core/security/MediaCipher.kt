package com.auroraclima.app.core.security

import java.io.InputStream
import java.io.OutputStream

/**
 * Cifrado por stream de los archivos adjuntos del historial (RF-33, RNF-01):
 * los bytes se cifran mientras se copian, sin cargar el archivo completo en
 * memoria — a diferencia de [PayloadCipher], pensado para payloads pequeños
 * que caben en un ByteArray.
 *
 * Es una interfaz por el mismo criterio que [PayloadCipher]: la capa `data` de
 * records se prueba en la JVM con un cifrador falso reversible; el real, con
 * llave del Android Keystore, se prueba instrumentado.
 */
interface MediaCipher {
    /**
     * Envuelve [target] para escribir cifrado: primero viaja el nonce en claro
     * (necesario para descifrar; no revela contenido) y después el sellado.
     * Cerrar el stream devuelto finaliza el cifrado y cierra [target].
     */
    fun sealing(target: OutputStream): OutputStream

    /**
     * Envuelve [source] para leer descifrado el formato de [sealing]. Lanza si
     * el material está corrupto o la llave se invalidó; quien consume traduce
     * a `Result` (§3 de ARQUITECTURA.md).
     */
    fun opening(source: InputStream): InputStream
}
