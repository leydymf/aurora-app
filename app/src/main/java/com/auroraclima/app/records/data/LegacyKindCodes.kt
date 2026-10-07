package com.auroraclima.app.records.data

/**
 * Códigos del catálogo fijo que el registro usó hasta la BD v4 y el payload v2
 * (ADR 2026-07-14 (c)) → id del tema publicado equivalente (ADR 2026-10-03,
 * cierre P-68). Único lugar del mapa: lo usan la migración v4→v5 de la BD
 * cifrada y la restauración de payloads v2 de otro dispositivo (RF-39). Los
 * literales ya existían en el DEX; los ids de destino son opacos (RNF-05).
 */
internal val legacyKindToTopicId: Map<String, String> = mapOf(
    "psychological" to "t02",
    "physical" to "t01",
    "sexual" to "t03",
    "economic" to "t04"
)

/** Traduce un código legado; cualquier otro valor se conserva tal cual (se mostrará neutro). */
internal fun String.toTopicIdIfLegacy(): String = legacyKindToTopicId[this] ?: this
