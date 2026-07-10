package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.usecase.SignInOutcome
import com.auroraclima.app.accounts.domain.usecase.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Inicio de sesión (RF-15). Con sesión vigente del proveedor salta directo a la
 * pantalla principal (RF-12 criterio 1); en la práctica, tras una salida del
 * espacio interno casi siempre habrá que iniciar sesión porque AuthSessionCleaner
 * cierra la del proveedor (D3 H-3) — el salto cubre muerte de proceso sin cierre
 * limpio. La contraseña vive solo en este estado y se limpia al usarse.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // replay = 1: el caso «ya hay sesión» emite desde init, antes de que la UI
    // llegue a suscribirse — sin replay ese evento se perdería y la pantalla
    // quedaría en blanco en lugar de saltar al panel.
    private val _openEvents = MutableSharedFlow<Unit>(replay = 1)

    /** Acceso concedido: continuar a la pantalla principal (criterio 1). */
    val openEvents: SharedFlow<Unit> = _openEvents.asSharedFlow()

    private var countdownJob: Job? = null

    init {
        if (repository.hasActiveSession()) {
            // isCheckingSession queda true: la pantalla no llega a pintar el formulario.
            _openEvents.tryEmit(Unit)
        } else {
            _uiState.update { it.copy(isCheckingSession = false) }
            // Un bloqueo vigente de una visita anterior se retoma donde iba.
            viewModelScope.launch { startLockCountdown(signIn.currentLockMillis()) }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorRes = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSubmit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            when (val outcome = signIn(current.email, current.password)) {
                SignInOutcome.Success -> {
                    // Higiene: la contraseña no permanece en memoria del estado.
                    _uiState.update { it.copy(isLoading = false, password = "") }
                    _openEvents.tryEmit(Unit)
                }
                SignInOutcome.RejectedCredentials -> _uiState.update {
                    it.copy(isLoading = false, errorRes = R.string.account_login_error)
                }
                is SignInOutcome.Locked -> {
                    _uiState.update { it.copy(isLoading = false, password = "", errorRes = null) }
                    startLockCountdown(outcome.remainingMillis)
                }
                SignInOutcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorRes = R.string.common_error_generic)
                }
            }
        }
    }

    /** Pinta la «pausa temporal» y la descuenta segundo a segundo (P08·b). */
    private fun startLockCountdown(remainingMillis: Long) {
        if (remainingMillis <= 0) return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = secondsCeil(remainingMillis)
            while (remaining > 0) {
                _uiState.update { it.copy(lockRemainingSeconds = remaining) }
                delay(MILLIS_PER_SECOND)
                // Re-anclado al deadline persistido en cada tick: delay se congela
                // en suspensión profunda (ADR 2026-07-09) y el texto no debe
                // desincronizarse del bloqueo real que verifica el caso de uso.
                remaining = secondsCeil(signIn.currentLockMillis())
            }
            _uiState.update { it.copy(lockRemainingSeconds = null) }
        }
    }

    private fun secondsCeil(millis: Long): Long = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
