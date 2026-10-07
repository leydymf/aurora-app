package com.auroraclima.app.core.security

/**
 * Punto de extensión de la reconciliación CON la nube (ADR 2026-10-06).
 *
 * Hermano de [AccountDataRestorer], con una diferencia esencial: el restaurador
 * repone un dispositivo VACÍO (RF-39 criterio 1) y solo baja; el reconciliador
 * corre sobre un dispositivo que YA tiene datos y hace converger los dos lados
 * — trae lo que otro dispositivo creó, editó o eliminó, y conserva lo que aquí
 * está pendiente de subir.
 *
 * Cada feature dueña de datos sincronizados aporta el suyo por `@IntoSet`.
 * Contrato: es de MEJOR ESFUERZO y silencioso (§3). Sin red, sin DEK o con un
 * documento ilegible no hace nada y no lanza; nunca produce un mensaje visible
 * ni escribe en logs (regla 4). El orden del conjunto no está garantizado, así
 * que un reconciliador que dependa de otro debe resolverlo dentro de sí mismo
 * (como el de registros, que repone los adjuntos después de los registros).
 */
fun interface AccountDataReconciler {
    suspend fun reconcile()
}
