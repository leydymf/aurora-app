package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.EntryKind
import java.time.LocalDate

/**
 * Estado de la creación (RF-32) y edición (RF-35) de un registro, mockup P19.
 * Los errores de validación son por campo y conviven con lo ya diligenciado:
 * señalar sin perder nada (criterio 2). El error de guardado solo admite un
 * recurso — jamás texto libre de una excepción (P-24, guía §3).
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
    @StringRes val loadErrorRes: Int? = null,
    @StringRes val kindErrorRes: Int? = null,
    @StringRes val detailErrorRes: Int? = null,
    @StringRes val saveErrorRes: Int? = null
)
