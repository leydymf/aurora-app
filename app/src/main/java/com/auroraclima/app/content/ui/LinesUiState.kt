package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.ContactLine

/** Estado de las líneas de atención (RF-23, mockup P14). */
data class LinesUiState(
    val isLoading: Boolean = true,
    val lines: List<ContactLine> = emptyList(),
    @StringRes val errorRes: Int? = null
)
