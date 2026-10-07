package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.OpaqueIdSource
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.data.local.RemovedRow
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historial personal sobre la BD cifrada central (RF-32, RF-34; RNF-01).
 *
 * El contrato P-36 (apertura perezosa + traducción a Result) vive en
 * [JournalStoreAccess], compartido con los adjuntos de RF-33.
 *
 * El tipo se persiste como id del tema publicado más la instantánea de su
 * nombre (ADR 2026-10-03): aquí no se valida contra ningún catálogo — un id
 * que ya no está publicado sigue siendo un registro legible, y la resolución
 * de su nombre y visual es de presentación.
 *
 * Desde el ADR 2026-10-06 cada registro nuevo nace con su nombre de documento
 * en la nube ([OpaqueIdSource]) y lleva la fecha de su última edición; el
 * borrado deja constancia para que la reconciliación no lo resucite mientras
 * el borrado remoto sigue en la cola.
 */
@Singleton
class LocalJournalRepository @Inject constructor(
    private val storeAccess: JournalStoreAccess,
    private val opaqueIds: OpaqueIdSource
) : JournalRepository {
    override suspend fun add(entry: NewJournalEntry): Result<Long> = storeAccess.withDatabase { db ->
        val now = System.currentTimeMillis()
        db.journalDao().insert(
            JournalEntryRow(
                docId = opaqueIds.next(),
                updatedAt = now,
                day = entry.occurredOn.toEpochDay(),
                kind = entry.kindId,
                label = entry.kindLabel,
                detail = entry.detail,
                createdAt = now
            )
        )
    }

    override suspend fun entries(): Result<List<JournalEntry>> = storeAccess.withDatabase { db ->
        db.journalDao().entries().map { it.toDomain() }
    }

    override suspend fun entry(id: Long): Result<JournalEntry?> = storeAccess.withDatabase { db ->
        db.journalDao().entryById(id)?.toDomain()
    }

    override suspend fun update(entry: JournalEntry): Result<Unit> = storeAccess.withDatabase { db ->
        db.journalDao().update(
            id = entry.id,
            day = entry.occurredOn.toEpochDay(),
            kind = entry.kindId,
            label = entry.kindLabel,
            detail = entry.detail,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Borra la fila y deja la constancia con su nombre de documento: si el
     * borrado remoto se queda en la cola (sin conexión), la reconciliación debe
     * saber que ese documento ya no se quiere, en vez de volver a bajarlo.
     */
    override suspend fun delete(id: Long): Result<String> = storeAccess.withDatabase { db ->
        val row = db.journalDao().entryById(id)
        db.journalDao().deleteById(id)
        if (row == null || row.docId.isEmpty()) return@withDatabase ""
        db.removedDao().insert(
            RemovedRow(docId = row.docId, scope = RemovedRow.SCOPE_ENTRY, at = System.currentTimeMillis())
        )
        row.docId
    }

    private fun JournalEntryRow.toDomain(): JournalEntry = JournalEntry(
        id = id,
        occurredOn = LocalDate.ofEpochDay(day),
        kindId = kind,
        kindLabel = label,
        detail = detail
    )
}
