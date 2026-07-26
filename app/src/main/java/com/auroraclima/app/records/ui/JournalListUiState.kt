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
    /**
     * Aviso genérico de una sola vez (RF-39): la contraseña fue restablecida
     * sin la anterior y el respaldo previo quedó ilegible; el respaldo se
     * reinició. Redacción honesta y sin tecnicismos, nada del esquema de llaves.
     */
    val showsBackupNotice: Boolean = false,
    @StringRes val errorRes: Int? = null
)
