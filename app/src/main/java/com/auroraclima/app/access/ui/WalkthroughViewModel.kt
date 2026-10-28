package com.auroraclima.app.access.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La guía de Aurora (v0.3.4; RF-10, RF-11): una sola experiencia que reemplaza al
 * tutorial y al recorrido por Inicio. Vive en la entrada del panel y muere con ella.
 *
 * Se decide una vez al crearse, consumiendo la marca sellada: con
 * [WalkthroughMark.FROM_START] (se deja al crear el código de acceso) empieza en
 * «Conoce Aurora»; con [WalkthroughMark.TO_FINISH] (la deja la parte 3 antes de la
 * salida real) muestra solo «¡Listo!». Un fallo de lectura equivale a ninguna marca:
 * la guía es una ayuda, nunca un error visible (§3). Desde Ajustes se repite completa
 * ([onReplay]).
 */
@HiltViewModel
class WalkthroughViewModel @Inject constructor(
    private val repository: WalkthroughRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalkthroughUiState())
    val uiState: StateFlow<WalkthroughUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (repository.consume().getOrNull()) {
                WalkthroughMark.FROM_START -> _uiState.update { it.copy(step = WalkthroughStep.OVERVIEW) }
                WalkthroughMark.TO_FINISH -> _uiState.update {
                    it.copy(
                        step = WalkthroughStep.DONE,
                        reachedEarly = false
                    )
                }
                null -> Unit
            }
        }
    }

    /** «Empezar», en «Conoce Aurora». */
    fun onStart() {
        if (_uiState.value.step == WalkthroughStep.OVERVIEW) moveTo(WalkthroughStep.TYPE_IN)
    }

    /** «Ahora no», en «Conoce Aurora»: a Inicio sin guía. */
    fun onNotNow() = close()

    /** «Siguiente» de una tarjeta. */
    fun onNext() {
        _uiState.value.step?.next?.let(::moveTo)
    }

    /** «Atrás» de una tarjeta. */
    fun onBack() {
        _uiState.value.step?.previous?.let(::moveTo)
    }

    /** «Saltar guía» (cualquier tarjeta) y «Después» (parte 3): a «¡Listo!» con «Para recordar:». */
    fun onFinishEarly() {
        if (_uiState.value.step?.isCard != true) return
        _uiState.update { it.copy(step = WalkthroughStep.DONE, reachedEarly = true) }
    }

    /** «Ir a mi espacio», en «¡Listo!». */
    fun onFinish() = close()

    /**
     * El gesto atrás: equivale a «Atrás»; en «Conoce Aurora», a «Ahora no»; en la
     * primera tarjeta (sin «Atrás»), vuelve a «Conoce Aurora»; en «¡Listo!», a «Ir a
     * mi espacio».
     */
    fun onBackGesture() {
        when (_uiState.value.step) {
            null -> Unit
            WalkthroughStep.OVERVIEW -> onNotNow()
            WalkthroughStep.DONE -> onFinish()
            WalkthroughStep.TYPE_IN -> moveTo(WalkthroughStep.OVERVIEW)
            else -> onBack()
        }
    }

    /** Volver a verla desde Ajustes: completa desde «Conoce Aurora», sin tocar la marca. */
    fun onReplay() {
        _uiState.value = WalkthroughUiState(step = WalkthroughStep.OVERVIEW)
    }

    private fun moveTo(step: WalkthroughStep) {
        _uiState.update { it.copy(step = step) }
    }

    private fun close() {
        _uiState.value = WalkthroughUiState()
    }
}

/**
 * [step] es el paso a la vista (null, sin guía). [reachedEarly]: «¡Listo!» llegó por
 * «Saltar guía» o «Después», sin la salida real (su texto es «Para recordar:»).
 */
data class WalkthroughUiState(
    val step: WalkthroughStep? = null,
    val reachedEarly: Boolean = false
)

/**
 * Los pasos de la guía en su orden: «Conoce Aurora», la parte 1 (Entrar), los seis de
 * la parte 2 (Tu espacio) en el orden pedido, la parte 3 (Salir) y «¡Listo!».
 *
 * Los nombres de las constantes llegan LEGIBLES al DEX del release (R8 los conserva
 * porque `name` y `valueOf` son reflexión): por eso son sosos (RNF-05, H-8).
 */
enum class WalkthroughStep(val part: Int, @StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    OVERVIEW(0, R.string.access_walk_intro_title, R.string.access_walk_intro_sub),
    TYPE_IN(1, R.string.access_walk_enter_title, R.string.access_walk_enter_body),
    GUIDE(2, R.string.access_walk_guide_title, R.string.access_walk_guide_body),
    JOURNAL(2, R.string.access_walk_journal_title, R.string.access_walk_journal_body),
    START(2, R.string.access_walk_start_title, R.string.access_walk_start_body),
    SAVED(2, R.string.access_walk_saved_title, R.string.access_walk_saved_body),
    SHORTCUTS(2, R.string.access_walk_shortcuts_title, R.string.access_walk_shortcuts_body),
    SETTINGS(2, R.string.access_walk_settings_title, R.string.access_walk_settings_body),
    CLOUD(3, R.string.access_walk_cloud_title, R.string.access_walk_cloud_body),
    DONE(4, R.string.access_walk_done_title, R.string.access_walk_done_back);

    /** Las tarjetas son las partes 1 a 3; «Conoce Aurora» y «¡Listo!» son pantallas completas. */
    val isCard: Boolean get() = part in CARD_PARTS

    /** La tarjeta anterior para «Atrás»; null donde no hay «Atrás» (la primera tarjeta y las pantallas). */
    val previous: WalkthroughStep?
        get() = if (isCard && this != TYPE_IN) entries[ordinal - 1] else null

    /**
     * La tarjeta siguiente para «Siguiente»; null donde no hay «Siguiente» (en la parte 3
     * la acción es tocar la nube, y las pantallas tienen sus propios botones).
     */
    val next: WalkthroughStep?
        get() = if (isCard && this != CLOUD) entries[ordinal + 1] else null

    /** Posición dentro de la parte 2 («N de 6»), desde 1; 0 fuera de ella. */
    val numberInSpace: Int get() = if (part == SPACE_PART) ordinal - GUIDE.ordinal + 1 else 0

    companion object {
        const val SPACE_PART = 2
        private val CARD_PARTS = 1..3

        /** Cuántos pasos tiene la parte 2. */
        val spaceCount: Int get() = entries.count { it.part == SPACE_PART }
    }
}
