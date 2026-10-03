package com.auroraclima.app.records.domain.repository

import com.auroraclima.app.records.domain.model.ExportDocument

/**
 * Salida del PDF cifrado (RF-45 criterio 5): o bien directo al destino que la
 * usuaria eligió en el selector del sistema (sin copia intermedia), o bien a
 * un temporal de la caché privada para entregarlo a la app que ella elija al
 * compartir. Todo devuelve [Result] (§3): un fallo se muestra como genérico.
 *
 * El temporal de [stage] queda YA cifrado con la contraseña de la usuaria y
 * se elimina con [clearStaged] al volver del selector, en cada cierre de
 * sesión y al abrir la pantalla; nunca sobrevive como copia útil.
 */
interface JournalExportRepository {
    /** Escribe el PDF al URI del documento creado por la usuaria (crear documento del sistema). */
    suspend fun exportTo(targetUri: String, document: ExportDocument, password: String): Result<Unit>

    /** Escribe el PDF a un temporal privado y devuelve el URI compartible (FileProvider). */
    suspend fun stage(document: ExportDocument, password: String): Result<String>

    /** Elimina el documento creado por el selector cuando la exportación no llegó a escribirlo (quedaría vacío). */
    suspend fun discard(targetUri: String): Result<Unit>

    /** Elimina todo temporal de exportación; idempotente. */
    suspend fun clearStaged(): Result<Unit>
}
