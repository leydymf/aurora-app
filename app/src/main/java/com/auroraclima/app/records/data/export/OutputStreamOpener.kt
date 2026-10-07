package com.auroraclima.app.records.data.export

import java.io.OutputStream

/**
 * Abre el destino que la usuaria eligió en «crear documento» del sistema
 * (RF-45 criterio 5). Se inyecta desde el ContentResolver real; en tests, un
 * falso que escribe a memoria o a un archivo.
 */
fun interface OutputStreamOpener {
    /** Stream de escritura del URI, o null si el proveedor no lo entrega. */
    fun open(uri: String): OutputStream?
}
