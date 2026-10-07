package com.auroraclima.app.core.catalog

/**
 * Contrato para leer el catálogo de categorías publicadas con las que la
 * usuaria clasifica sus registros (RF-32 refinado el 2026-10-03, cierre P-68):
 * son los mismos temas publicados del módulo informativo (RF-21), en el mismo
 * orden y con el mismo visual.
 *
 * `records` solo conoce esta interfaz; la implementación vive en `content`,
 * dueño de los datos (inversión de dependencias: las features no se importan
 * entre sí, §2 de ARQUITECTURA.md — mismo patrón que `DirectoryProvider`).
 * La disponibilidad sin conexión la aporta la caché persistente de la fuente
 * (la misma que usa la Guía, RF-25). Nombres neutros de catálogo (RNF-05).
 */
interface CategoryCatalog {
    /**
     * Categorías publicadas, ordenadas. Devuelve [Result]; la excepción no
     * cruza a la UI (§3). Una lista vacía es un resultado válido (nada
     * publicado aún) y quien consume decide la degradación.
     */
    suspend fun categories(): Result<List<Category>>
}

/**
 * Una categoría publicada. [icon] y [accent] son ids de la biblioteca cerrada
 * de `core/ui/v2` (esquema v2, ADR 2026-08-14); nulos o desconocidos degradan
 * al mapeo automático por id, igual que en la Guía.
 */
data class Category(
    val id: String,
    val order: Int,
    val label: String,
    val icon: String?,
    val accent: String?
)
