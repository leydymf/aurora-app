package com.auroraclima.app.content.data

import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.content.ui.FakeContentRepository
import com.auroraclima.app.core.catalog.Category
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de categorías del registro (cierre P-68) es una proyección
 * fiel de los temas publicados de la Guía: mismo orden, mismo nombre y mismo
 * visual seleccionado (esquema v2); el fallo viaja como Result (§3).
 */
class CategoryContentProviderTest {
    private val repository = FakeContentRepository()
    private val provider = CategoryContentProvider(repository)

    @Test
    fun `proyecta los temas publicados con su orden, nombre y visual`() = runTest {
        repository.topicsResult = Result.success(
            listOf(
                topic("t01", order = 1, title = "Primera", icon = "figure", accent = "blue"),
                topic("t05", order = 5, title = "Quinta", icon = "device", accent = "lilac"),
                topic("v1", order = 7, title = "Sin visual")
            )
        )

        val categories = provider.categories().getOrThrow()

        assertEquals(
            listOf(
                Category("t01", 1, "Primera", "figure", "blue"),
                Category("t05", 5, "Quinta", "device", "lilac"),
                Category("v1", 7, "Sin visual", null, null)
            ),
            categories
        )
    }

    @Test
    fun `sin temas publicados devuelve una lista vacia como exito`() = runTest {
        assertEquals(emptyList<Category>(), provider.categories().getOrThrow())
    }

    @Test
    fun `un fallo del repositorio se propaga como Result sin lanzar`() = runTest {
        repository.topicsResult = Result.failure(IOException("sin red"))

        assertTrue(provider.categories().isFailure)
    }

    private fun topic(id: String, order: Int, title: String, icon: String? = null, accent: String? = null) = Topic(
        id = id,
        order = order,
        title = title,
        summary = "resumen",
        signals = emptyList(),
        examples = emptyList(),
        icon = icon,
        accent = accent
    )
}
