package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.model.ExportSummary
import com.auroraclima.app.records.domain.model.ExportSummaryRow
import com.auroraclima.app.records.domain.model.ResolvedKind
import com.auroraclima.app.records.domain.model.resolveKind

/**
 * Estado de la pantalla de exportación (RF-45). El interruptor de adjuntos
 * nace APAGADO (criterio 3) y la contraseña se valida en línea (criterio 4:
 * largo mínimo y confirmación). [isSaved] solo se enciende tras «Guardar»;
 * al compartir la app no sabe si el envío ocurrió y no afirma nada.
 * Errores solo como recurso (§3).
 */
data class JournalExportUiState(
    val isLoading: Boolean = true,
    val summary: ExportSummary? = null,
    val kinds: List<EntryKind> = emptyList(),
    val includeAttachments: Boolean = false,
    val password: String = "",
    val confirmation: String = "",
    val isPasswordVisible: Boolean = false,
    val showsPasswordError: Boolean = false,
    val isExporting: Boolean = false,
    val isSaved: Boolean = false,
    @StringRes val errorRes: Int? = null
) {
    /** Mensaje de validación (solo tras intentar exportar, para no regañar mientras escribe). */
    @get:StringRes
    val passwordErrorRes: Int?
        get() = when {
            !showsPasswordError -> null
            !ExportPolicy.isPasswordLongEnough(password) -> R.string.journal_export_password_short
            password != confirmation -> R.string.journal_export_password_mismatch
            else -> null
        }

    val isPasswordValid: Boolean
        get() = ExportPolicy.isPasswordLongEnough(password) && password == confirmation

    val canExport: Boolean
        get() = summary != null && !isExporting && !isSaved

    fun kindOf(row: ExportSummaryRow): ResolvedKind = resolveKind(row.kindId, row.kindLabel, kinds)

    /** La contraseña jamás sale en una representación de texto del estado (auditoría I-2). */
    override fun toString(): String {
        val flags = "includeAttachments=$includeAttachments, isExporting=$isExporting, isSaved=$isSaved"
        return "JournalExportUiState(isLoading=$isLoading, entries=${summary?.entryCount}, $flags, errorRes=$errorRes)"
    }
}

/** Lanzamientos al sistema de consumo único (RF-45 criterio 5). */
sealed interface ExportLaunch {
    /** Abrir «crear documento» con el nombre neutro sugerido. */
    data class Save(val suggestedName: String) : ExportLaunch

    /** Abrir el selector de apps con el URI del temporal cifrado. */
    data class Share(val uri: String) : ExportLaunch
}
