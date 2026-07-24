package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Estado del listado del historial (RF-34, mockup P18). El orden lo garantiza
 * el repositorio (cronológico inverso); el error solo admite un recurso (§3).
 * [attachmentCounts] pinta el «· N ADJUNTOS» de cada fila (RF-33, P18); si su
 * lectura falla queda vacío y las filas se pintan sin contador.
 */
data class JournalListUiState(
    val isLoading: Boolean = true,
    val entries: List<JournalEntry> = emptyList(),
    val attachmentCounts: Map<Long, Int> = emptyMap(),
    @StringRes val errorRes: Int? = null
)
