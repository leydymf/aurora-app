package com.auroraclima.app.core.security

import java.io.InputStream
import java.io.OutputStream

/**
 * Cifrado por stream del VIAJE de las evidencias a la nube (RF-39, cambio de
 * política D0 §5.3 en curso): mismo contrato que [MediaCipher] pero con la DEK
 * recuperable del sobre doble en lugar de la llave del dispositivo — lo que se
 * sube así puede abrirse en cualquier aparato con la contraseña.
 *
 * Los archivos EN DISCO siguen sellados con la llave del Keystore
 * ([KeystoreMediaCipher]): si el respaldo rota la DEK, el teléfono viejo debe
 * seguir leyendo sus propios adjuntos para re-subirlos — guardar lo local bajo
 * la DEK lo dejaría ciego tras una rotación. Este cifrador solo existe para el
 * trayecto dispositivo↔nube (subida y restauración).
 *
 * Interfaz por el criterio de siempre: la capa `data` se prueba en JVM con un
 * falso reversible; la implementación real ([DekMediaCipher]) también es
 * `javax.crypto` puro y se prueba en JVM con cripto real.
 */
interface RecoverableMediaCipher {
    /**
     * Envuelve [target] para escribir cifrado con la DEK: primero viaja el
     * nonce en claro y después el sellado. Cerrar el stream devuelto finaliza
     * el cifrado y cierra [target]. Lanza sin DEK establecida (§3).
     */
    fun sealing(target: OutputStream): OutputStream

    /**
     * Envuelve [source] para leer descifrado el formato de [sealing]. Lanza si
     * el material está corrupto, es de otra DEK o no hay DEK establecida.
     */
    fun opening(source: InputStream): InputStream
}
