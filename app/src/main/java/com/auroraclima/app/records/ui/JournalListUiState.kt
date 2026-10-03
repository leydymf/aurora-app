package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.ResolvedKind
import com.auroraclima.app.records.domain.model.resolveKind

/**
 * Estado del listado del historial (RF-34, mockup P18). El orden lo garantiza
 * el repositorio (cronológico inverso); el error solo admite un recurso (§3).
 * [attachmentCounts] pinta el «· N ADJUNTOS» de cada fila (RF-33, P18); si su
 * lectura falla queda vacío y las filas se pintan sin contador. [kinds] son los
 * temas publicados (P-68): si su lectura falla, cada fila cae a la instantánea
 * de su nombre y al visual neutro — el listado nunca falla por el catálogo.
 *
 * Modo de selección (RF-45 criterio 1): [isSelecting] convierte cada fila en
 * una casilla y el botón inferior en «Continuar»; [selectedIds] nace VACÍO —
 * nada viene marcado por defecto — y solo guarda ids, nunca contenido.
 */
data class JournalListUiState(
    val isLoading: Boolean = true,
    val entries: List<JournalEntry> = emptyList(),
    val attachmentCounts: Map<Long, Int> = emptyMap(),
    val kinds: List<EntryKind> = emptyList(),
    /**
     * Aviso genérico de una sola vez (RF-39): la contraseña fue restablecida
     * sin la anterior y el respaldo previo quedó ilegible; el respaldo se
     * reinició. Redacción honesta y sin tecnicismos, nada del esquema de llaves.
     */
    val showsBackupNotice: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val isSelecting: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
) {
    /** «Continuar» solo con al menos un registro marcado (RF-45 criterio 1). */
    val canContinue: Boolean
        get() = selectedIds.isNotEmpty()

    /** «Seleccionar» solo tiene sentido con registros cargados y sin error. */
    val canSelect: Boolean
        get() = !isLoading && errorRes == null && entries.isNotEmpty()

    fun kindOf(entry: JournalEntry): ResolvedKind = resolveKind(entry.kindId, entry.kindLabel, kinds)
}
