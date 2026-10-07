package com.auroraclima.app.core.security

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de identificadores opacos para los documentos que viajan a la nube.
 *
 * Son 32 caracteres hexadecimales de [SecureRandom] — la misma forma que ya
 * usaban los archivos del almacén de adjuntos. Dos motivos:
 *
 * - **Sin colisiones entre dispositivos.** Mientras el id del documento remoto
 *   fue el id local autoincremental, dos teléfonos de la misma cuenta podían
 *   generar el mismo id para registros distintos y pisarse en la nube. Con la
 *   reconciliación hacia abajo eso dejaría de ser un documento perdido para
 *   convertirse en un registro que REEMPLAZA al otro también en el teléfono.
 * - **Sin metadatos.** Un id de secuencia deja inferir cuántos registros hay y
 *   con qué frecuencia se crean (P-48); 32 hex aleatorios no dicen nada.
 *
 * Es interfaz para que las pruebas inyecten una fuente determinista.
 */
fun interface OpaqueIdSource {
    fun next(): String
}

@Singleton
class SecureRandomOpaqueIds @Inject constructor() : OpaqueIdSource {
    override fun next(): String {
        val bytes = ByteArray(ID_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** 16 bytes = 32 hex: colisión despreciable y forma idéntica a la ya usada. */
        const val ID_SIZE_BYTES = 16
    }
}
