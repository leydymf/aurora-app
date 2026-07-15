package com.auroraclima.app.records.domain.repository

import com.auroraclima.app.records.domain.model.JournalEntry

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
 */
interface JournalSyncRepository {
    /** Sube (o re-sube, idempotente) un registro ya guardado localmente. */
    suspend fun push(entry: JournalEntry): Result<Unit>

    /** Borra en la nube el registro [entryId] (preparación de RF-36/RF-20; sin UI aún). */
    suspend fun remove(entryId: Long): Result<Unit>
}
