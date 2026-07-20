package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Estado del detalle de un registro (RF-34, mockup P20), con la eliminación en
 * dos pasos (RF-36, mockup P20·b). Un id que ya no existe se trata como el
 * error genérico: sin detalles técnicos (§3, P-24).
 */
data class JournalDetailUiState(
    val isLoading: Boolean = true,
    val entry: JournalEntry? = null,
    @StringRes val errorRes: Int? = null,
    // Eliminación (RF-36 criterio 1): el diálogo es el segundo paso; su error
    // también es solo genérico.
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    @StringRes val deleteErrorRes: Int? = null
)
