package com.auroraclima.app.content.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombres de los argumentos de navegación del detalle de entidad (neutros: quedan en el APK). */
const val ROUTE_ID_ARG = "routeId"
const val AGENCY_ID_ARG = "agencyId"

/**
 * Detalle de una entidad de un sector (RF-22 criterio 2): qué hace, cuándo acudir
 * y cómo contactarla. Ids inexistentes o fallos → mensaje genérico (P-24, guía §3).
 */
@HiltViewModel
class AgencyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ContentRepository
) : ViewModel() {
    private val routeId: String = requireNotNull(savedStateHandle[ROUTE_ID_ARG]) {
        "Falta el argumento de navegación $ROUTE_ID_ARG"
    }
    private val agencyId: String = requireNotNull(savedStateHandle[AGENCY_ID_ARG]) {
        "Falta el argumento de navegación $AGENCY_ID_ARG"
    }

    private val _uiState = MutableStateFlow(AgencyDetailUiState())
    val uiState: StateFlow<AgencyDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            repository.getRoutes()
                .onSuccess { routes ->
                    val route = routes.firstOrNull { it.id == routeId }
                    val agency = route?.agencies?.firstOrNull { it.id == agencyId }
                    _uiState.update {
                        if (route != null && agency != null) {
                            it.copy(isLoading = false, routeTitle = route.title, agency = agency)
                        } else {
                            it.copy(isLoading = false, errorRes = R.string.content_error_generic)
                        }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorRes = R.string.content_error_generic) }
                }
        }
    }
}
