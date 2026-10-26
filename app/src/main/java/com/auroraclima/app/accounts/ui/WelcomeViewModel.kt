package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.accounts.domain.usecase.DiscardUnfinishedActivationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Bienvenida: primera pantalla del flujo de activación (la puerta de búsqueda la
 * abre con `OpenActivation`). No recolecta ni persiste nada; su única dependencia
 * BORRA: aquí se descarta lo que haya dejado un registro sin terminar (hallazgo A
 * de v0.3.2), que antes vivía en el consentimiento. Se lanza sin esperarlo; el login
 * y la creación de cuenta lo vuelven a pedir antes de hablar con el proveedor.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    discardUnfinished: DiscardUnfinishedActivationUseCase
) : ViewModel() {
    init {
        viewModelScope.launch { discardUnfinished() }
    }
}
