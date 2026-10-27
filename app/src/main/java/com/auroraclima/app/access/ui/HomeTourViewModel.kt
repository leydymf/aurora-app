package com.auroraclima.app.access.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.HomeTourRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Recorrido guiado por Inicio (v0.3.3). Se decide una vez al crearse, con la entrada
 * del panel, y consume la marca al leerla: aparece por sí solo una sola vez, aunque la
 * sesión se cierre a mitad (nube, inactividad, segundo plano). En ese caso no se
 * repite ni se retoma en la siguiente entrada: queda en Ajustes, «Ver el recorrido por
 * la app» ([onReplay]). Así nunca hay que guardar en qué paso iba.
 */
@HiltViewModel
class HomeTourViewModel @Inject constructor(
    private val repository: HomeTourRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeTourUiState())
    val uiState: StateFlow<HomeTourUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Un fallo de lectura equivale a «no pendiente»: el recorrido es una ayuda,
            // nunca un error visible (§3).
            if (repository.consumePending().getOrDefault(false)) {
                _uiState.update { it.copy(step = HomeTourStep.first) }
            }
        }
    }

    /** «Siguiente»; en el último paso («Entendido») termina. */
    fun onNext() {
        _uiState.update { it.copy(step = it.step?.next) }
    }

    /** «Saltar» y el gesto atrás: termina sin recorrer el resto. */
    fun onSkip() {
        _uiState.update { it.copy(step = null) }
    }

    /** Volver a verlo desde Ajustes: empieza desde el primer paso, sin tocar la marca. */
    fun onReplay() {
        _uiState.update { it.copy(step = HomeTourStep.first) }
    }
}

/** [step] es el paso a la vista; null, sin recorrido. */
data class HomeTourUiState(val step: HomeTourStep? = null)

/**
 * Pasos del recorrido, en su orden. Cada uno ilumina un elemento de Inicio (o la nube)
 * y lo presenta con un título y una frase.
 *
 * Los nombres de las constantes llegan LEGIBLES al DEX del release (R8 los conserva
 * porque `name` y `valueOf` son reflexión): por eso son sosos (RNF-05, H-8).
 */
enum class HomeTourStep(@StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    START(R.string.access_tour_start_title, R.string.access_tour_start_body),
    SAVED(R.string.access_tour_saved_title, R.string.access_tour_saved_body),
    GUIDE(R.string.access_tour_guide_title, R.string.access_tour_guide_body),
    JOURNAL(R.string.access_tour_journal_title, R.string.access_tour_journal_body),
    SETTINGS(R.string.access_tour_settings_title, R.string.access_tour_settings_body),
    SHORTCUTS(R.string.access_tour_shortcuts_title, R.string.access_tour_shortcuts_body),
    CLOUD(R.string.access_tour_cloud_title, R.string.access_tour_cloud_body);

    /** Posición para el contador («1 de 7»), desde 1. */
    val number: Int get() = ordinal + 1

    val isLast: Boolean get() = next == null

    /** El paso siguiente; null después del último. */
    val next: HomeTourStep? get() = entries.getOrNull(ordinal + 1)

    companion object {
        val first: HomeTourStep get() = entries.first()
        val count: Int get() = entries.size
    }
}
