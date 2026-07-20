package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.DeleteAccountResult
import com.auroraclima.app.accounts.domain.usecase.DeleteAccountUseCase
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
 * Eliminación total de la cuenta (RF-20). El rechazo de contraseña usa el MISMO
 * mensaje genérico del inicio de sesión (indistinguibilidad, RF-15 criterio 2);
 * la falta de red tiene mensaje propio porque la promesa importa: sin conexión
 * nada se ha borrado. El estado (incluida la contraseña) vive solo aquí y muere
 * con el pop de la ruta — jamás en rememberSaveable.
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val deleteAccount: DeleteAccountUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _doneEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Todo eliminado: la pantalla dispara el cierre de sesión y el regreso al clima. */
    val doneEvents: SharedFlow<Unit> = _doneEvents.asSharedFlow()

    fun onStartConfirmation() {
        _uiState.update { it.copy(step = DeleteAccountStep.ConfirmScope, errorRes = null) }
    }

    fun onScopeConfirmed() {
        _uiState.update { it.copy(step = DeleteAccountStep.ConfirmPassword) }
    }

    /** Cancelar cualquier diálogo regresa al resumen; la contraseña no se retiene. */
    fun onDismissDialogs() {
        if (_uiState.value.isWorking) return
        _uiState.update {
            it.copy(step = DeleteAccountStep.Overview, password = "", errorRes = null)
        }
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
        _uiState.update { it.copy(isWorking = true, errorRes = null) }
        viewModelScope.launch {
            when (deleteAccount(current.password)) {
                DeleteAccountResult.Success -> {
                    // Higiene: la contraseña no permanece en el estado.
                    _uiState.update { it.copy(isWorking = false, password = "") }
                    _doneEvents.tryEmit(Unit)
                }
                DeleteAccountResult.InvalidCredentials -> _uiState.update {
                    it.copy(isWorking = false, errorRes = R.string.account_login_error)
                }
                DeleteAccountResult.NoConnection -> _uiState.update {
                    it.copy(isWorking = false, errorRes = R.string.account_delete_error_offline)
                }
                DeleteAccountResult.GenericFailure -> _uiState.update {
                    it.copy(isWorking = false, errorRes = R.string.account_delete_error_generic)
                }
            }
        }
    }
}
