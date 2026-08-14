package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.topicVisual

/**
 * Tipos de la Guía (RF-21, mockup P10 / v2-12). Desde la S3 ya no es la portada:
 * la pestaña «Guía» abre [GuideHomeScreen] y esta pantalla vuelve a ser un
 * destino con retorno; los atajos a rutas y líneas viven en la portada. Cada
 * tipo lleva el icono y matiz de la asignación fija por id ([topicVisual], D2).
 */
@Composable
fun TopicsRoute(onBack: () -> Unit, onTopicSelected: (String) -> Unit) {
    val viewModel: TopicsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    TopicsScreen(
        state = state,
        onBack = onBack,
        onTopicSelected = onTopicSelected,
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun TopicsScreen(
    state: TopicsUiState,
    onBack: () -> Unit,
    onTopicSelected: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        V2BackHeader(
            tag = stringResource(R.string.content_info_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null -> V2ErrorState(messageRes = R.string.content_error_generic, onRetry = onRetry)
            else -> TopicsContent(state = state, onTopicSelected = onTopicSelected)
        }
    }
}

@Composable
private fun TopicsContent(state: TopicsUiState, onTopicSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_topics_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_topics_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Una tarjeta por tipo (v2-12): el resumen viene de Firestore y se
        // trunca a dos líneas — el texto completo vive en el detalle.
        state.topics.forEachIndexed { index, topic ->
            if (index > 0) Spacer(modifier = Modifier.height(14.dp))
            val visual = topicVisual(topic.id, topic.icon, topic.accent)
            SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                IconListRow(
                    iconRes = visual.iconRes,
                    accent = visual.accent,
                    title = topic.title,
                    supportingText = topic.summary,
                    supportingMaxLines = 2,
                    onClick = { onTopicSelected(topic.id) },
                    trailing = { RowChevron() }
                )
            }
        }
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}
