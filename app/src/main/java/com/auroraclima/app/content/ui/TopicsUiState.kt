package com.auroraclima.app.content.ui

import androidx.annotation.StringRes
import com.auroraclima.app.content.domain.model.Topic

/** Estado de la portada del módulo de información (RF-21, mockup P10). */
data class TopicsUiState(
    val isLoading: Boolean = true,
    val topics: List<Topic> = emptyList(),
    @StringRes val errorRes: Int? = null
)
