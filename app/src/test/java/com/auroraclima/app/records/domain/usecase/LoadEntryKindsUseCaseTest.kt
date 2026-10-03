package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.EntryKind
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los tipos del registro son la proyección fiel del catálogo publicado (cierre P-68). */
class LoadEntryKindsUseCaseTest {
    private val catalog = FakeCategoryCatalog()
    private val useCase = LoadEntryKindsUseCase(catalog)

    @Test
    fun `proyecta cada categoria con su id, nombre y visual en el mismo orden`() = runTest {
        val kinds = useCase().getOrThrow()

        assertEquals(
            listOf(
                EntryKind("t01", "Primera", "figure", "blue"),
                EntryKind("t02", "Segunda", "lobes", "lilac"),
                EntryKind("t03", "Tercera", "slash_circle", "pink"),
                EntryKind("t04", "Cuarta", "card", "sand")
            ),
            kinds
        )
    }

    @Test
    fun `un fallo del catalogo se propaga como Result`() = runTest {
        catalog.result = Result.failure(IOException("sin red"))

        assertTrue(useCase().isFailure)
    }
}
