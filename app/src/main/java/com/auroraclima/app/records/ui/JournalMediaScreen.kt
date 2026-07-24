package com.auroraclima.app.records.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist

/**
 * Visor interno de adjuntos (RF-33 criterio 2: solo dentro de la app; la ruta
 * vive en el grafo seguro, que bloquea capturas y recientes por construcción —
 * RNF-02). Imagen a pantalla completa; PDF página a página con renderizado
 * bajo demanda. Sin compartir, sin exportar, sin abrir en apps externas:
 * cualquier salida rompería el camuflaje (decisión de sesión, ADR RF-33).
 */
@Composable
fun JournalMediaRoute(onBack: () -> Unit) {
    val viewModel: JournalMediaViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    JournalMediaScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        renderPage = viewModel::page
    )
}

@Composable
internal fun JournalMediaScreen(
    state: JournalMediaUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    renderPage: suspend (Int, Int) -> ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
    ) {
        BackHeader(
            tag = stringResource(R.string.journal_media_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> LoadingState()
            state.errorRes != null -> ErrorState(messageRes = state.errorRes, onRetry = onRetry)
            state.image != null -> ImageViewer(state.image)
            state.pageCount > 0 -> DocumentViewer(pageCount = state.pageCount, renderPage = renderPage)
            else -> ErrorState(messageRes = R.string.common_error_generic, onRetry = onRetry)
        }
    }
}

@Composable
private fun ImageViewer(image: ImageBitmap) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DocumentViewer(pageCount: Int, renderPage: suspend (Int, Int) -> ImageBitmap?) {
    val density = LocalDensity.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        items((0 until pageCount).toList(), key = { it }) { index ->
            DocumentPage(index = index, renderPage = renderPage, densityDpi = density.density)
        }
    }
}

@Composable
private fun DocumentPage(index: Int, renderPage: suspend (Int, Int) -> ImageBitmap?, densityDpi: Float) {
    // Ancho objetivo acotado (gama baja, RNF-07): nítido en pantalla sin
    // renderizar más píxeles de los que se ven.
    val targetWidth = (360 * densityDpi).toInt().coerceIn(320, 1440)
    val page by produceState<ImageBitmap?>(initialValue = null, index) {
        value = renderPage(index, targetWidth)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = CloudWhite
    ) {
        val rendered = page
        if (rendered == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            )
        } else {
            Image(
                bitmap = rendered,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(rendered.width.toFloat() / rendered.height)
            )
        }
    }
}
