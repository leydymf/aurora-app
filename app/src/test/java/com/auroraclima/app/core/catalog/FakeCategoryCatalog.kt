package com.auroraclima.app.core.catalog

/** Fake del catálogo publicado para los ViewModels del registro (guía §5: fake, no mock). */
class FakeCategoryCatalog : CategoryCatalog {
    var result: Result<List<Category>> = Result.success(defaultCategories)
    var calls = 0
        private set

    override suspend fun categories(): Result<List<Category>> {
        calls++
        return result
    }

    companion object {
        /** Los cuatro temas base tal como los publica el seed (ids opacos, visual v2). */
        val defaultCategories = listOf(
            Category("t01", 1, "Primera", "figure", "blue"),
            Category("t02", 2, "Segunda", "lobes", "lilac"),
            Category("t03", 3, "Tercera", "slash_circle", "pink"),
            Category("t04", 4, "Cuarta", "card", "sand")
        )
    }
}
