package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.Agency

/** Estado del detalle de una entidad (RF-22 criterio 2, mockup P13). */
data class AgencyDetailUiState(
    val isLoading: Boolean = true,
    val routeTitle: String = "",
    val agency: Agency? = null,
    @StringRes val errorRes: Int? = null
)
