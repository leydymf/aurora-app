package com.auroraclima.app.content.data

import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.content.domain.repository.ContentRepository
import com.auroraclima.app.core.catalog.Category
import com.auroraclima.app.core.catalog.CategoryCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [CategoryCatalog] con los temas de content (RF-21): es
 * content, dueño de los temas publicados, quien los expone al registro a
 * través del contrato de `core` (§2, cierre P-68). El repositorio ya filtra
 * los esquemas no soportados, omite documentos incompletos y ordena por
 * `order`, así que aquí solo se proyecta; el fallo viaja como [Result] sin que
 * la excepción cruce a la UI (§3). Misma caché persistente que la Guía.
 */
@Singleton
class CategoryContentProvider @Inject constructor(
    private val repository: ContentRepository
) : CategoryCatalog {
    override suspend fun categories(): Result<List<Category>> =
        repository.getTopics().map { topics -> topics.map { it.toCategory() } }
}

private fun Topic.toCategory() = Category(id = id, order = order, label = title, icon = icon, accent = accent)
