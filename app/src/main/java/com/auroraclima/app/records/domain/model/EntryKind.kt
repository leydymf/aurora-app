package com.auroraclima.app.records.domain.model

/**
 * Un tipo elegible en el selector del registro (RF-32 refinado 2026-10-03):
 * proyección de un tema publicado de la Guía (RF-21) con su nombre y el visual
 * seleccionado en el esquema v2. Ya no es un catálogo fijo del dominio — la
 * lista llega del contrato `CategoryCatalog` de core y cambia cuando la
 * administradora publica, sin versión nueva de la app (cierre P-68).
 */
data class EntryKind(
    val id: String,
    val label: String,
    val icon: String?,
    val accent: String?
)
