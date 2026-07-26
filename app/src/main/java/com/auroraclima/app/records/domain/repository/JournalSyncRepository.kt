package com.auroraclima.app.records.domain.repository

/**
 * Sincronización cifrada del historial con la nube (RF-37). El contenido del
 * registro se cifra EN EL CLIENTE antes de subir: el proyecto de Firebase solo
 * recibe blobs opacos y el acceso queda restringido a la cuenta de la usuaria
 * (reglas de `firestore.rules`).
 *
 * Es una operación de mejor esfuerzo y silenciosa (§3): su [Result] existe para
 * ser probado, pero quien la invoca (el caso de uso de guardado) lo ignora — un
 * fallo de red jamás rompe el guardado local ni asoma un error revelador. La cola
 * offline→online la aporta la persistencia del propio Firestore (RF-38 fuera de
 * alcance: no hay outbox propio).
 *
 * Desde RF-39 el push va POR ID: la implementación lee la fila local (fuente de
 * verdad, incluido `created_at` — el orden debe sobrevivir a la restauración),
 * la sella con la DEK recuperable y deja constancia de la generación con la que
 * subió; un push que no llega a sellarse queda pendiente para el reconciliador.
 */
interface JournalSyncRepository {
    /** Sube (o re-sube, idempotente) el registro local [entryId] ya guardado. */
    suspend fun push(entryId: Long): Result<Unit>

    /** Borra en la nube el registro [entryId] (RF-36/RF-20). */
    suspend fun remove(entryId: Long): Result<Unit>

    /**
     * Restauración multi-dispositivo (RF-39 criterio 1): descarga los
     * documentos de la cuenta, los descifra con la DEK recuperable y repone los
     * registros — con sus MISMOS ids y su orden — SOLO si el historial local
     * está vacío (dispositivo nuevo o reinstalación); mezclar con datos vivos
     * podría resucitar registros con borrado aún en cola. Lo indescifrable
     * (blobs v1 de la llave vieja, restos de otra generación) se omite en
     * silencio.
     */
    suspend fun restore(): Result<Unit>
}
