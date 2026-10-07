package com.auroraclima.app.records.domain.model

/**
 * Cómo se presenta el tipo de un registro una vez contrastado con el catálogo
 * publicado (ADR 2026-10-03). Publicado: nombre y visual vivos del catálogo.
 * Retirado o sin tipo: [isPublished] falso, [label] con el último nombre
 * conocido (null si nunca se conoció) y sin visual — la capa de presentación
 * pinta el rótulo neutro y el visual neutro. El registro nunca se rompe por un
 * tipo que ya no existe (tolerancia estilo RF-25).
 */
data class ResolvedKind(
    val id: String,
    val label: String?,
    val icon: String?,
    val accent: String?,
    val isPublished: Boolean
)

/** Un tipo publicado tal como lo ofrece el selector. */
fun EntryKind.toResolved(): ResolvedKind =
    ResolvedKind(id = id, label = label, icon = icon, accent = accent, isPublished = true)

/**
 * Contrasta el tipo guardado con el catálogo. Función pura del dominio: un
 * catálogo vacío o no disponible (lista vacía) degrada TODOS los registros a su
 * instantánea sin fallar.
 */
fun resolveKind(kindId: String, kindLabel: String, catalog: List<EntryKind>): ResolvedKind {
    val published = catalog.firstOrNull { it.id == kindId }
    return if (published != null) {
        published.toResolved()
    } else {
        ResolvedKind(
            id = kindId,
            label = kindLabel.ifBlank { null },
            icon = null,
            accent = null,
            isPublished = false
        )
    }
}
