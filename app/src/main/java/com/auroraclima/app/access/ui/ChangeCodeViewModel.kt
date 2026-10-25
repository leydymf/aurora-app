package com.auroraclima.app.access.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.usecase.ChangeSecretCodeResult
import com.auroraclima.app.access.domain.usecase.ChangeSecretCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cambio de la clave personal (RF-09). Mismo patrón que CodeSetupViewModel,
 * incluida la confirmación del código nuevo (v0.3.2).
 */
@HiltViewModel
class ChangeCodeViewModel @Inject constructor(
    private val changeSecretCode: ChangeSecretCodeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangeCodeUiState())
    val uiState: StateFlow<ChangeCodeUiState> = _uiState.asStateFlow()

    private val _doneEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Clave cambiada: la pantalla regresa a configuración. */
    val doneEvents: SharedFlow<Unit> = _doneEvents.asSharedFlow()

    private var checkJob: Job? = null

    fun onCurrentCodeChange(value: String) {
        _uiState.update {
            it.copy(currentCode = value, currentErrorRes = null, generalErrorRes = null)
        }
    }

    fun onNewCodeChange(value: String) {
        _uiState.update { it.copy(newCode = value, confirmErrorRes = null, generalErrorRes = null) }
        // Reglas en vivo (RF-08 criterio 2): solo la última tecla cuenta.
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val checks = changeSecretCode.check(value)
            _uiState.update {
                it.copy(hasMinLength = checks.hasMinLength, isNotRealCity = checks.isNotRealCity)
            }
        }
    }

    fun onConfirmCodeChange(value: String) {
        _uiState.update { it.copy(confirmCode = value, confirmErrorRes = null, generalErrorRes = null) }
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
        // Mismo criterio que la creación (CodeSetupViewModel): se comparan las
        // formas recortadas, que es lo que se hashea, y un desacuerdo no toca
        // el código vigente. El mensaje no repite ninguno de los dos códigos.
        if (current.newCode.trim() != current.confirmCode.trim()) {
            _uiState.update { it.copy(confirmErrorRes = R.string.access_setup_error_mismatch) }
            return
        }
        _uiState.update {
            it.copy(isLoading = true, currentErrorRes = null, confirmErrorRes = null, generalErrorRes = null)
        }
        viewModelScope.launch { applyResult(changeSecretCode(current.currentCode, current.newCode)) }
    }

    private fun applyResult(result: ChangeSecretCodeResult) {
        when (result) {
            ChangeSecretCodeResult.Changed -> {
                // Higiene de estado sensible: ninguna clave permanece en
                // memoria del estado una vez guardado el hash.
                _uiState.update {
                    it.copy(isLoading = false, currentCode = "", newCode = "", confirmCode = "")
                }
                _doneEvents.tryEmit(Unit)
            }
            ChangeSecretCodeResult.CurrentCodeMismatch -> _uiState.update {
                it.copy(isLoading = false, currentErrorRes = R.string.access_change_error_current)
            }
            is ChangeSecretCodeResult.Rejected -> _uiState.update {
                it.copy(
                    isLoading = false,
                    hasMinLength = result.checks.hasMinLength,
                    isNotRealCity = result.checks.isNotRealCity,
                    generalErrorRes = R.string.access_setup_error_rules
                )
            }
            ChangeSecretCodeResult.StorageFailure -> _uiState.update {
                it.copy(isLoading = false, generalErrorRes = R.string.common_error_generic)
            }
        }
    }
}
