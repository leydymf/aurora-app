package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import java.time.LocalDate

/**
 * Estado de la creación (RF-32) y edición (RF-35) de un registro, mockup P19.
 * Los errores de validación son por campo y conviven con lo ya diligenciado:
 * señalar sin perder nada (criterio 2). El error de guardado solo admite un
 * recurso — jamás texto libre de una excepción (P-24, guía §3).
 *
 * Adjuntos (RF-33): la sección «Adjuntos» del mockup P19 con las tres vías de
 * la política (galería, cámara del sistema, archivo). Su error también es solo
 * un recurso genérico; el «máximo alcanzado» es el único mensaje específico —
 * no revela nada y evita un rechazo inexplicable.
 */
data class JournalEditorUiState(
    val occurredOn: LocalDate = LocalDate.now(),
    // Un registro es un hecho ya ocurrido: el selector no permite fechas
    // futuras (P-47a). `today` fija el tope al abrir el editor.
    val today: LocalDate = LocalDate.now(),
    val kind: EntryKind? = null,
    val detail: String = "",
    // Modo edición (RF-35): la pantalla es la misma; cambian el rótulo y el
    // destino del guardado. `isLoading` cubre la carga del registro a editar.
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isPickingDate: Boolean = false,
    val isSaving: Boolean = false,
    val attachments: List<JournalAttachment> = emptyList(),
    val isImporting: Boolean = false,
    @StringRes val loadErrorRes: Int? = null,
    @StringRes val kindErrorRes: Int? = null,
    @StringRes val detailErrorRes: Int? = null,
    @StringRes val saveErrorRes: Int? = null,
    @StringRes val mediaErrorRes: Int? = null
) {
    /** Botones de adjuntar habilitados: hay cupo y no hay importación en curso. */
    val canAttach: Boolean
        get() = !isImporting && AttachmentPolicy.hasCapacity(attachments.size)
}
