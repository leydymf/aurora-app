package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.ChangePasswordResult
import com.auroraclima.app.accounts.domain.usecase.ChangePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cambio de contraseña de la cuenta (RF-39). Mismo patrón que
 * ChangeCodeViewModel (RF-09); los errores son genéricos y jamás distinguen
 * qué credencial falló más allá del campo señalado (criterio de RF-15/RF-20).
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePassword: ChangePasswordUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _doneEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Contraseña cambiada: la pantalla regresa a configuración. */
    val doneEvents: SharedFlow<Unit> = _doneEvents.asSharedFlow()

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, currentErrorRes = null, generalErrorRes = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update {
            it.copy(newPassword = value, newErrorRes = null, confirmErrorRes = null, generalErrorRes = null)
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmErrorRes = null, generalErrorRes = null) }
    }

    fun onToggleCurrentVisibility() {
        _uiState.update { it.copy(isCurrentVisible = !it.isCurrentVisible) }
    }

    fun onToggleNewVisibility() {
        _uiState.update { it.copy(isNewVisible = !it.isNewVisible) }
    }

    fun onToggleConfirmVisibility() {
        _uiState.update { it.copy(isConfirmVisible = !it.isConfirmVisible) }
    }

    fun onSubmit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        // La coincidencia se comprueba localmente antes de tocar la red (RF-07).
        if (current.newPassword != current.confirmPassword) {
            _uiState.update { it.copy(confirmErrorRes = R.string.account_error_password_mismatch) }
            return
        }
        _uiState.update {
            it.copy(isLoading = true, currentErrorRes = null, newErrorRes = null, generalErrorRes = null)
        }
        viewModelScope.launch {
            when (changePassword(current.currentPassword, current.newPassword)) {
                ChangePasswordResult.Success -> {
                    // Higiene: ninguna contraseña permanece en memoria del estado.
                    _uiState.update {
                        it.copy(isLoading = false, currentPassword = "", newPassword = "", confirmPassword = "")
                    }
                    _doneEvents.tryEmit(Unit)
                }
                ChangePasswordResult.InvalidCredentials -> _uiState.update {
                    it.copy(isLoading = false, currentErrorRes = R.string.account_login_error)
                }
                ChangePasswordResult.WeakPassword -> _uiState.update {
                    it.copy(isLoading = false, newErrorRes = R.string.account_error_password_short)
                }
                ChangePasswordResult.NoConnection -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.account_password_change_offline)
                }
                ChangePasswordResult.GenericFailure -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.common_error_generic)
                }
            }
        }
    }
}
