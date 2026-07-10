package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.Route

/** Estado de la triada de sectores con sus entidades (RF-22, mockup P12). */
data class RoutesUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    @StringRes val errorRes: Int? = null
)
