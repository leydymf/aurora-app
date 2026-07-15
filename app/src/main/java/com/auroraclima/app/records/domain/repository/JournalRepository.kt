package com.auroraclima.app.records.domain.repository

import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry

/**
 * Historial personal protegido (RF-32, RF-34). Toda operación devuelve [Result]:
 * la apertura de la BD cifrada puede fallar (blob de la passphrase corrupto,
 * llave del Keystore invalidada — ADR D6) y esa excepción jamás cruza a la UI
 * (§3, P-36); se muestra como error genérico.
 */
interface JournalRepository {
    /** Guarda un registro nuevo (RF-32) y devuelve su id local (docId de la sync, RF-37). */
    suspend fun add(entry: NewJournalEntry): Result<Long>

    /** Todos los registros, del más reciente al más antiguo (RF-34 criterio 1). */
    suspend fun entries(): Result<List<JournalEntry>>

    /** Un registro por id para el detalle (RF-34); null si ya no existe. */
    suspend fun entry(id: Long): Result<JournalEntry?>
}
