package com.auroraclima.app.records.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Nombre del argumento de navegación del visor (neutro: queda en el APK). */
const val MEDIA_ID_ARG = "mediaId"

/**
 * Visor interno de un adjunto (RF-33 criterio 2: visible SOLO dentro de la
 * app). Imagen: bytes descifrados → bitmap en memoria, sin tocar disco. PDF:
 * el repositorio deja una copia descifrada temporal, aquí se abre su
 * descriptor y el archivo SE ELIMINA en el acto — el descriptor sigue vivo sin
 * entrada en el sistema de archivos (la ventana de texto claro en disco dura
 * milisegundos; un resto por muerte de proceso lo barre `sweepOrphans`).
 *
 * El renderizado de páginas es bajo demanda y serializado ([renderMutex]):
 * PdfRenderer solo admite una página abierta a la vez. La caché acota memoria
 * en PDFs largos.
 */
@HiltViewModel
class JournalMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val attachmentRepository: JournalAttachmentRepository
) : ViewModel() {
    // Argumento malformado o ausente → mismo trato que un adjunto inexistente (§3).
    private val mediaId: Long? = savedStateHandle.get<String>(MEDIA_ID_ARG)?.toLongOrNull()

    private val _uiState = MutableStateFlow(JournalMediaUiState())
    val uiState: StateFlow<JournalMediaUiState> = _uiState.asStateFlow()

    private var renderer: PdfRenderer? = null
    private val renderMutex = Mutex()
    private val pageCache = LinkedHashMap<Int, ImageBitmap>()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    /** Página [index] del PDF al ancho [targetWidth]; null si ya no puede renderizarse. */
    suspend fun page(index: Int, targetWidth: Int): ImageBitmap? = renderMutex.withLock {
        pageCache[index]?.let { return it }
        val activeRenderer = renderer ?: return null
        withContext(Dispatchers.IO) {
            runCatching {
                activeRenderer.openPage(index).use { page ->
                    val width = max(1, targetWidth)
                    val height = max(1, (width.toFloat() / page.width * page.height).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }?.also { rendered ->
            pageCache[index] = rendered
            // Acotar memoria: se conservan solo las páginas más recientes.
            while (pageCache.size > PAGE_CACHE_SIZE) {
                pageCache.remove(pageCache.keys.first())
            }
        }
    }

    private fun load() {
        val id = mediaId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            attachmentRepository.content(id)
                .onSuccess { content ->
                    when (content) {
                        is AttachmentContent.Image -> showImage(content)
                        is AttachmentContent.Document -> showDocument(content)
                        null -> showError()
                    }
                }
                .onFailure { showError() }
        }
    }

    private suspend fun showImage(content: AttachmentContent.Image) {
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size)?.asImageBitmap()
        }
        if (bitmap == null) {
            showError()
        } else {
            _uiState.update { it.copy(isLoading = false, image = bitmap) }
        }
    }

    private suspend fun showDocument(content: AttachmentContent.Document) {
        val opened = withContext(Dispatchers.IO) {
            val file = File(content.path)
            try {
                runCatching {
                    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                }.getOrNull()
            } finally {
                // Eliminada SIEMPRE — también si la apertura falla (auditoría E1
                // M-2): con descriptor abierto, el contenido sigue vivo sin
                // archivo; sin él, no debe quedar nada descifrado en disco.
                file.delete()
            }
        }
        if (opened == null) {
            showError()
        } else {
            renderMutex.withLock {
                renderer?.close()
                renderer = opened
                pageCache.clear()
            }
            _uiState.update { it.copy(isLoading = false, pageCount = opened.pageCount) }
        }
    }

    private fun showError() {
        _uiState.update { it.copy(isLoading = false, errorRes = R.string.common_error_generic) }
    }

    override fun onCleared() {
        runCatching { renderer?.close() }
        renderer = null
        pageCache.clear()
    }

    private companion object {
        const val PAGE_CACHE_SIZE = 6
    }
}
