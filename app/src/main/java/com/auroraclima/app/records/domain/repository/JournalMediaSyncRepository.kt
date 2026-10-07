package com.auroraclima.app.records.domain.repository

/**
 * Sincronización cifrada de las evidencias con la nube (RF-39; cambio de
 * política D0 §5.3 aprobado por la usuaria, pendiente de ratificación de la
 * directora). Mismo contrato de mejor esfuerzo y silencio que
 * [JournalSyncRepository]: el adjunto se sella EN EL CLIENTE con la DEK
 * recuperable, viaja troceado (límite de 1 MiB por documento de la capa
 * gratuita) y ningún fallo rompe el guardado local ni asoma un error.
 */
interface JournalMediaSyncRepository {
    /**
     * Sube (o re-sube, idempotente por `ref`) el adjunto local [attachmentId]
     * ya reclamado por un registro; los adjuntos en preparación no suben.
     * El éxito deja constancia de la generación en la fila.
     */
    suspend fun push(attachmentId: Long): Result<Unit>

    /**
     * Restauración multi-dispositivo: descarga las cabeceras, arma cada
     * adjunto desde sus trozos, lo descifra con la DEK y lo repone en el
     * almacén local (re-sellado con la llave del dispositivo) — SOLO si el
     * almacén está vacío y solo para adjuntos cuyo registro dueño exista
     * localmente (corre después de la restauración de registros). Lo
     * indescifrable o incompleto se omite en silencio.
     */
    suspend fun restore(): Result<Unit>

    /**
     * Hace converger los adjuntos con la nube (ADR 2026-10-06) sobre un
     * dispositivo que ya tiene datos: trae los que otro dispositivo adjuntó y
     * elimina los que allá se eliminaron. Corre DESPUÉS de la reconciliación de
     * registros, porque un adjunto necesita a su registro dueño.
     */
    suspend fun reconcile(): Result<Unit>
}
