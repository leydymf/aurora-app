package com.auroraclima.app.access.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.usecase.CreateSecretCodeResult
import com.auroraclima.app.access.domain.usecase.CreateSecretCodeUseCase
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
 * Creación de la clave personal (RF-08). La clave en claro vive únicamente en
 * este estado mientras la usuaria la teclea: al guardarse queda solo el hash
 * (criterio 3) y los campos se limpian; jamás toca Firebase ni logs.
 */
@HiltViewModel
class CodeSetupViewModel @Inject constructor(
    private val createSecretCode: CreateSecretCodeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodeSetupUiState())
    val uiState: StateFlow<CodeSetupUiState> = _uiState.asStateFlow()

    private val _continueEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Clave guardada: continúa el flujo del primer uso (RF-08 criterio 1). */
    val continueEvents: SharedFlow<Unit> = _continueEvents.asSharedFlow()

    private var checkJob: Job? = null

    fun onCodeChange(value: String) {
        _uiState.update {
            it.copy(code = value, confirmErrorRes = null, generalErrorRes = null)
        }
        // Reglas en vivo (criterio 2): solo la última tecla cuenta.
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val checks = createSecretCode.check(value)
            _uiState.update {
                it.copy(hasMinLength = checks.hasMinLength, isNotRealCity = checks.isNotRealCity)
            }
        }
    }

    fun onConfirmCodeChange(value: String) {
        _uiState.update {
            it.copy(confirmCode = value, confirmErrorRes = null, generalErrorRes = null)
        }
    }

    fun onToggleCodeVisibility() {
        _uiState.update { it.copy(isCodeVisible = !it.isCodeVisible) }
    }

    fun onToggleConfirmVisibility() {
        _uiState.update { it.copy(isConfirmVisible = !it.isConfirmVisible) }
    }

    fun onSubmit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        // La confirmación compara las formas recortadas: el caso de uso hashea la
        // clave recortada, y un espacio accidental no debe fabricar un desacuerdo.
        if (current.code.trim() != current.confirmCode.trim()) {
            _uiState.update { it.copy(confirmErrorRes = R.string.access_setup_error_mismatch) }
            return
        }
        _uiState.update { it.copy(isLoading = true, confirmErrorRes = null, generalErrorRes = null) }
        viewModelScope.launch {
            when (val result = createSecretCode(current.code)) {
                CreateSecretCodeResult.Created -> {
                    // Higiene de estado sensible: la clave no permanece en memoria
                    // del estado una vez guardado el hash.
                    _uiState.update { it.copy(isLoading = false, code = "", confirmCode = "") }
                    _continueEvents.tryEmit(Unit)
                }
                is CreateSecretCodeResult.Rejected -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasMinLength = result.checks.hasMinLength,
                        isNotRealCity = result.checks.isNotRealCity,
                        generalErrorRes = R.string.access_setup_error_rules
                    )
                }
                CreateSecretCodeResult.StorageFailure -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.common_error_generic)
                }
            }
        }
    }
}
