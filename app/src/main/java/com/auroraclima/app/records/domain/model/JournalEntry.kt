package com.auroraclima.app.records.domain.model

import java.time.LocalDate

/**
 * Un registro del historial personal (RF-32): qué pasó, cuándo y de qué tipo.
 *
 * El léxico de todo el feature es de diario personal («journal», «entry») por
 * RNF-05/P-36: los nombres de clase quedan legibles en el DEX hasta R8 (P-05),
 * los de tabla y columna viajan en los literales SQL que Room genera, y ninguno
 * puede sugerir el propósito real.
 *
 * [kindId] es el id del tema publicado que clasifica el hecho (ADR 2026-10-03,
 * cierre P-68) — un id opaco de la Guía, o [UNSPECIFIED_KIND_ID] cuando el
 * catálogo no estaba disponible al guardar. [kindLabel] es la instantánea del
 * nombre del tema en ese momento (`""` si no se conocía): si el tema deja de
 * publicarse, el registro conserva su último nombre conocido.
 */
data class JournalEntry(
    val id: Long,
    val occurredOn: LocalDate,
    val kindId: String,
    val kindLabel: String,
    val detail: String
)

/** Datos de un registro aún sin guardar (la BD asigna el id al insertar). */
data class NewJournalEntry(
    val occurredOn: LocalDate,
    val kindId: String,
    val kindLabel: String,
    val detail: String
)

/** Id reservado: el registro se guardó sin tema disponible y puede clasificarse después. */
const val UNSPECIFIED_KIND_ID = ""
