package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.AttachmentContent
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Visor interno de adjuntos (RF-33 criterio 2): id malformado, adjunto
 * inexistente y fallo del repositorio convergen en el error genérico
 * (P-24/P-36); la imagen descifrada se decodifica a bitmap en memoria. El
 * camino del PDF (PdfRenderer + unlink) va instrumentado/manual — el
 * renderizador del framework no existe fiable en la JVM.
 *
 * Robolectric por BitmapFactory (decodifica los bytes del fake); no valida
 * píxeles, solo el contrato del estado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class JournalMediaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val attachments = FakeJournalAttachmentRepository()

    private fun viewModel(id: String = "7") = JournalMediaViewModel(
        SavedStateHandle(mapOf(MEDIA_ID_ARG to id)),
        attachments
    )

    /** JPEG diminuto real, para que la decodificación tenga bytes válidos. */
    private fun tinyJpeg(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output))
        return output.toByteArray()
    }

    @Test
    fun `una imagen descifrada se expone como bitmap en memoria`() = runTest {
        attachments.contentResult = Result.success(AttachmentContent.Image(tinyJpeg()))

        val viewModel = viewModel()
        // La decodificación corre en Dispatchers.Default (hilo real, fuera del
        // reloj virtual): se espera el estado final, no el scheduler.
        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.isLoading)
        assertNull(state.errorRes)
        assertNotNull(state.image)
        assertEquals(0, state.pageCount)
    }

    @Test
    fun `un argumento malformado muestra el error generico sin lanzar`() = runTest {
        // Auditoría C3 H-1, mismo criterio que detalle y editor.
        val viewModel = viewModel(id = "no-numerico")
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `un adjunto que ya no existe muestra el error generico`() = runTest {
        attachments.contentResult = Result.success(null)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `un fallo del repositorio muestra solo el error generico`() = runTest {
        attachments.contentResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar tras un fallo recupera el contenido`() = runTest {
        attachments.contentResult = Result.failure(IllegalStateException("transitorio"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)

        attachments.contentResult = Result.success(AttachmentContent.Image(tinyJpeg()))
        viewModel.onRetry()
        // Igual que arriba: la decodificación termina en un hilo real.
        val state = viewModel.uiState.first { it.image != null || it.errorRes != null }

        assertNull(state.errorRes)
        assertNotNull(state.image)
    }

    @Test
    fun `sin renderizador una pagina pedida devuelve null`() = runTest {
        attachments.contentResult = Result.success(AttachmentContent.Image(tinyJpeg()))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.page(index = 0, targetWidth = 720))
    }
}
