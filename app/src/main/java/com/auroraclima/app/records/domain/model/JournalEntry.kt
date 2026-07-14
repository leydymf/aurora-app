package com.auroraclima.app.records.domain.model

import java.time.LocalDate

/**
 * Un registro del historial personal (RF-32): qué pasó, cuándo y de qué tipo.
 *
 * El léxico de todo el feature es de diario personal («journal», «entry») por
 * RNF-05/P-36: los nombres de clase quedan legibles en el DEX hasta R8 (P-05),
 * los de tabla y columna viajan en los literales SQL que Room genera, y ninguno
 * puede sugerir el propósito real.
 */
data class JournalEntry(
    val id: Long,
    val occurredOn: LocalDate,
    val kind: EntryKind,
    val detail: String
)

/** Datos de un registro aún sin guardar (la BD asigna el id al insertar). */
data class NewJournalEntry(
    val occurredOn: LocalDate,
    val kind: EntryKind,
    val detail: String
)

/**
 * Tipo del hecho registrado, alineado con los cuatro tipos de la Ley 1257 que
 * siembra el módulo de información (`topics` t01–t04). Es un catálogo fijo del
 * dominio y no una referencia a Firestore: la creación de un registro debe
 * funcionar sin red (RNF-08) y las features no se importan entre sí (§2).
 *
 * [storageCode] es el valor persistido: estable aunque el enum se renombre.
 */
enum class EntryKind(val storageCode: String) {
    PSYCHOLOGICAL("psychological"),
    PHYSICAL("physical"),
    SEXUAL("sexual"),
    ECONOMIC("economic");

    companion object {
        fun fromStorageCode(code: String): EntryKind? = entries.firstOrNull { it.storageCode == code }
    }
}
