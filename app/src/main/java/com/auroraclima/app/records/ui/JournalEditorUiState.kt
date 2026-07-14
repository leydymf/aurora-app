package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.EntryKind
import java.time.LocalDate

/**
 * Estado de la creación de un registro (RF-32, mockup P19). Los errores de
 * validación son por campo y conviven con lo ya diligenciado: señalar sin
 * perder nada (criterio 2). El error de guardado solo admite un recurso —
 * jamás texto libre de una excepción (P-24, guía §3).
 */
data class JournalEditorUiState(
    val occurredOn: LocalDate = LocalDate.now(),
    val kind: EntryKind? = null,
    val detail: String = "",
    val isPickingDate: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val kindErrorRes: Int? = null,
    @StringRes val detailErrorRes: Int? = null,
    @StringRes val saveErrorRes: Int? = null
)
