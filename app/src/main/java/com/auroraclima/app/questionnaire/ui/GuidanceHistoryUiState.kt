package com.auroraclima.app.questionnaire.ui

import androidx.annotation.StringRes
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome

/**
 * Estado del listado de resultados guardados (RF-31). [notice] es el aviso de
 * alcance (RF-30) del documento administrable: se muestra fijo también aquí —
 * un resultado guardado sigue siendo un resultado. Si el documento no puede
 * leerse, el listado no se cae (tolerancia estilo RF-25): el aviso ya acompañó
 * al resultado al calcularse y al guardarse.
 */
data class GuidanceHistoryUiState(
    val isLoading: Boolean = true,
    @StringRes val errorRes: Int? = null,
    val outcomes: List<SavedOutcome> = emptyList(),
    val notice: String? = null,
    /** Resultado abierto en la hoja de detalle; null = sin hoja. */
    val detail: SavedOutcome? = null,
    /** Segundo paso de la eliminación (doble confirmación) visible. */
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    @StringRes val deleteErrorRes: Int? = null
)
