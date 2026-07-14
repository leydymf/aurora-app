package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Estado del listado del historial (RF-34, mockup P18). El orden lo garantiza
 * el repositorio (cronológico inverso); el error solo admite un recurso (§3).
 */
data class JournalListUiState(
    val isLoading: Boolean = true,
    val entries: List<JournalEntry> = emptyList(),
    @StringRes val errorRes: Int? = null
)
