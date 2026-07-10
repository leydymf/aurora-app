package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.Topic

/** Estado del detalle de un tipo (RF-21 criterio 2, mockup P11). */
data class TopicDetailUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null,
    @StringRes val errorRes: Int? = null
)
