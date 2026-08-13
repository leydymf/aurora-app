package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.Agency

/**
 * Estado del detalle de una entidad (RF-22 criterio 2, mockup P13). [routeId]
 * existe para que la pantalla pinte el icono/matiz fijo del sector (v2-15,
 * decisión D2) — dato de presentación que ya viajaba en la ruta de navegación.
 */
data class AgencyDetailUiState(
    val isLoading: Boolean = true,
    val routeId: String = "",
    val routeTitle: String = "",
    val agency: Agency? = null,
    @StringRes val errorRes: Int? = null
)
