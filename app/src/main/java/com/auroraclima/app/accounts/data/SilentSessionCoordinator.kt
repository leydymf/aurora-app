package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.data.di.SilentSessionScope
import com.auroraclima.app.accounts.domain.model.SilentSessionState
import com.auroraclima.app.accounts.domain.repository.SilentSessionOrchestrator
import com.auroraclima.app.accounts.domain.usecase.SilentSignInOutcome
import com.auroraclima.app.accounts.domain.usecase.SilentSignInUseCase
import com.auroraclima.app.core.network.ConnectivityMonitor
import com.auroraclima.app.core.security.SensitiveStateCleaner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orquesta el reingreso silencioso (P-31 opción C) por fuera de los ViewModels
 * del grafo seguro: el pop del grafo los destruye y la confirmación en segundo
 * plano debe sobrevivir mientras la sesión interna siga viva. Ciclo:
 *
 * - [start] al conceder la entrada con blob: intenta el login silencioso.
 * - Éxito → [SilentSessionState.SignedIn], nada visible.
 * - Rechazo definitivo o blob ilegible → [SilentSessionState.LoginRequired] y
 *   un evento en [loginRequests]: la navegación interpone la pantalla de
 *   contraseña (el blob ya fue destruido por el caso de uso).
 * - Fallo transitorio → [SilentSessionState.Offline] (el chip sobrio «Sin
 *   conexión» deriva de aquí) y reintento SOLO cuando la red regresa — nunca
 *   en caliente contra un servicio caído.
 *
 * Es [SensitiveStateCleaner]: toda salida del espacio interno (rápida,
 * inactividad, background) cancela el trabajo y regresa a [SilentSessionState.Idle].
 * El blob NO se toca aquí — sobrevivir a la salida es el punto de la opción C.
 */
@Singleton
class SilentSessionCoordinator @Inject constructor(
    private val silentSignIn: SilentSignInUseCase,
    private val connectivity: ConnectivityMonitor,
    @SilentSessionScope private val scope: CoroutineScope
) : SilentSessionOrchestrator, SensitiveStateCleaner {
    private val _state = MutableStateFlow(SilentSessionState.Idle)
    override val state: StateFlow<SilentSessionState> = _state.asStateFlow()

    // Sin replay: una petición de login solo vale para la sesión interna que la
    // produjo — el cleaner corre antes del pop, así que nada queda pendiente.
    private val _loginRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** El silencioso fue rechazado: interponer la pantalla de contraseña. */
    override val loginRequests: SharedFlow<Unit> = _loginRequests.asSharedFlow()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        _state.value = SilentSessionState.Checking
        job = scope.launch {
            while (true) {
                when (silentSignIn()) {
                    SilentSignInOutcome.Success -> {
                        _state.value = SilentSessionState.SignedIn
                        return@launch
                    }
                    SilentSignInOutcome.Rejected,
                    SilentSignInOutcome.NoCredentials -> {
                        _state.value = SilentSessionState.LoginRequired
                        _loginRequests.tryEmit(Unit)
                        return@launch
                    }
                    SilentSignInOutcome.Unavailable -> {
                        _state.value = SilentSessionState.Offline
                        awaitNetworkReturn()
                        _state.value = SilentSessionState.Checking
                    }
                }
            }
        }
    }

    /**
     * Espera el próximo REGRESO de la red. Si el fallo ocurrió con red viva
     * (servicio caído), exige verla caer y volver: sin reintentos en caliente.
     */
    private suspend fun awaitNetworkReturn() {
        var seenOffline = false
        connectivity.isOnline.first { isOnline ->
            if (!isOnline) seenOffline = true
            isOnline && seenOffline
        }
    }

    override fun onSessionCleanup() {
        job?.cancel()
        job = null
        _state.value = SilentSessionState.Idle
    }
}
