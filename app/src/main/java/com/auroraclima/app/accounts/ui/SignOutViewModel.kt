package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import com.auroraclima.app.accounts.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cierre de sesión explícito (RF-19): destruye el blob del reingreso silencioso
 * y SOLO DESPUÉS emite [doneEvents], para que la navegación cierre la sesión
 * interna (closeNow) con el blob ya inexistente — si el orden se invirtiera,
 * el pop destruiría este ViewModel con el borrado a medio camino.
 */
@HiltViewModel
class SignOutViewModel @Inject constructor(
    private val signOut: SignOutUseCase
) : ViewModel() {
    // replay = 1 por el mismo motivo que en LoginViewModel: el evento no debe
    // perderse si la suscripción de la pantalla llega después de emitirlo.
    private val _doneEvents = MutableSharedFlow<Unit>(replay = 1)

    /** Blob destruido: continuar con la rutina única de cierre (RF-19 criterio 2). */
    val doneEvents: SharedFlow<Unit> = _doneEvents.asSharedFlow()

    fun onSignOut() {
        signOut()
        _doneEvents.tryEmit(Unit)
    }
}
