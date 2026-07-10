package com.auroraclima.app.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.usecase.CreateAccountUseCase
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

/** Creación de cuenta con correo y contraseña (RF-07). */
@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val createAccount: CreateAccountUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    private val _continueEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Cuenta creada: continúa a la definición del código (RF-07 criterio 1). */
    val continueEvents: SharedFlow<Unit> = _continueEvents.asSharedFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailErrorRes = null, generalErrorRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(password = value, passwordErrorRes = null, confirmErrorRes = null, generalErrorRes = null)
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmErrorRes = null, generalErrorRes = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onToggleConfirmVisibility() {
        _uiState.update { it.copy(isConfirmVisible = !it.isConfirmVisible) }
    }

    fun onSubmit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        // La coincidencia se comprueba localmente antes de tocar la red: una
        // contraseña mal repetida no debe generar tráfico ni cuenta a medias.
        if (current.password != current.confirmPassword) {
            _uiState.update { it.copy(confirmErrorRes = R.string.account_error_password_mismatch) }
            return
        }
        _uiState.update {
            it.copy(isLoading = true, emailErrorRes = null, passwordErrorRes = null, generalErrorRes = null)
        }
        viewModelScope.launch {
            when (createAccount(current.email, current.password)) {
                CreateAccountResult.Success -> {
                    // Higiene de estado sensible: la contraseña no permanece en
                    // memoria del estado una vez usada.
                    _uiState.update { it.copy(isLoading = false, password = "", confirmPassword = "") }
                    _continueEvents.tryEmit(Unit)
                }
                CreateAccountResult.InvalidEmail -> showError {
                    it.copy(emailErrorRes = R.string.account_error_email_format)
                }
                CreateAccountResult.EmailAlreadyInUse -> showError {
                    it.copy(emailErrorRes = R.string.account_error_email_taken)
                }
                CreateAccountResult.WeakPassword -> showError {
                    it.copy(passwordErrorRes = R.string.account_error_password_short)
                }
                CreateAccountResult.GenericFailure -> showError {
                    it.copy(generalErrorRes = R.string.common_error_generic)
                }
            }
        }
    }

    private fun showError(transform: (CreateAccountUiState) -> CreateAccountUiState) {
        _uiState.update { transform(it.copy(isLoading = false)) }
    }
}
