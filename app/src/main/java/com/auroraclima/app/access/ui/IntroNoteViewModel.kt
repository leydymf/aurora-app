package com.auroraclima.app.access.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.access.domain.repository.IntroNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aviso de bienvenida del Inicio para quien saltó el tutorial: dónde está y cómo
 * volver. Se decide una vez al crearse, con la entrada del panel, y consume la
 * marca al leerla — por eso aparece una sola vez aunque la usuaria no lo cierre
 * y salga con la nube. Mientras siga en esta entrada del panel se mantiene a la
 * vista hasta que lo cierre o abra el tutorial.
 */
@HiltViewModel
class IntroNoteViewModel @Inject constructor(
    private val repository: IntroNoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(IntroNoteUiState())
    val uiState: StateFlow<IntroNoteUiState> = _uiState.asStateFlow()

    private val _openTutorialEvents = Channel<Unit>(Channel.BUFFERED)

    /** «Ver el tutorial»: el destino lo decide el NavHost. De un solo consumo (ADR 2026-10-25). */
    val openTutorialEvents: Flow<Unit> = _openTutorialEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Un fallo de lectura equivale a «no pendiente»: el aviso es una ayuda,
            // nunca un error visible (§3).
            if (repository.consumePending().getOrDefault(false)) {
                _uiState.update { it.copy(isVisible = true) }
            }
        }
    }

    fun onDismiss() {
        _uiState.update { it.copy(isVisible = false) }
    }

    fun onOpenTutorial() {
        onDismiss()
        _openTutorialEvents.trySend(Unit)
    }
}

data class IntroNoteUiState(val isVisible: Boolean = false)
