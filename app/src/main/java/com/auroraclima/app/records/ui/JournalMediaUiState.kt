package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Estado del visor interno de adjuntos (RF-33 criterio 2; dentro del grafo
 * seguro — RNF-02 cubre capturas y recientes). O una imagen ya decodificada,
 * o un PDF con su número de páginas (cada página se pinta bajo demanda desde
 * el ViewModel). El error es solo el genérico (§3, P-24).
 */
data class JournalMediaUiState(
    val isLoading: Boolean = true,
    val image: ImageBitmap? = null,
    val pageCount: Int = 0,
    @StringRes val errorRes: Int? = null
)
