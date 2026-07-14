package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Estado del detalle de un registro (RF-34, mockup P20). Un id que ya no
 * existe se trata como el error genérico: sin detalles técnicos (§3, P-24).
 */
data class JournalDetailUiState(
    val isLoading: Boolean = true,
    val entry: JournalEntry? = null,
    @StringRes val errorRes: Int? = null
)
