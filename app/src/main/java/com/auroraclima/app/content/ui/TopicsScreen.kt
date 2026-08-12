package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Tipos de la Guía (RF-21, mockup P10 / v2-12). Desde la S3 ya no es la portada:
 * la pestaña «Guía» abre [GuideHomeScreen] y esta pantalla vuelve a ser un
 * destino con retorno; los atajos a rutas y líneas viven en la portada.
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
            .background(Mist)
            .systemBarsPadding()
    ) {
        BackHeader(
            tag = stringResource(R.string.content_info_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> LoadingState()
            state.errorRes != null -> ErrorState(messageRes = R.string.content_error_generic, onRetry = onRetry)
            else -> TopicsContent(
                state = state,
                onTopicSelected = onTopicSelected
            )
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
        SectionTitle(stringResource(R.string.content_topics_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_topics_subtitle),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(20.dp))
        ContentRowGroup {
            state.topics.forEachIndexed { index, topic ->
                if (index > 0) ContentRowDivider()
                ContentListRow(
                    title = topic.title,
                    summary = topic.summary,
                    onClick = { onTopicSelected(topic.id) }
                )
            }
        }
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}
