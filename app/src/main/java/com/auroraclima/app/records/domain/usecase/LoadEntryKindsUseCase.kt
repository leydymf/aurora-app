package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.core.catalog.Category
import com.auroraclima.app.core.catalog.CategoryCatalog
import com.auroraclima.app.records.domain.model.EntryKind
import javax.inject.Inject

/**
 * Tipos elegibles del registro (RF-32 refinado 2026-10-03, cierre P-68): los
 * temas publicados de la Guía leídos por el contrato de core, proyectados al
 * modelo del feature. Caso de uso único para que el editor, el listado y el
 * detalle compartan la misma lectura (§3); el fallo viaja como [Result] y cada
 * pantalla decide su degradación (opción neutra, instantánea del nombre).
 */
class LoadEntryKindsUseCase @Inject constructor(
    private val catalog: CategoryCatalog
) {
    suspend operator fun invoke(): Result<List<EntryKind>> =
        catalog.categories().map { categories -> categories.map { it.toEntryKind() } }
}

private fun Category.toEntryKind() = EntryKind(id = id, label = label, icon = icon, accent = accent)
