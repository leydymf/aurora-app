package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.topicVisual

/** Detalle de un tipo (RF-21, mockup P11 / v2-13): señales con vistos, ejemplos y salida a rutas. */
@Composable
fun TopicDetailRoute(onBack: () -> Unit, onOpenRoutes: () -> Unit) {
    val viewModel: TopicDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    TopicDetailScreen(
        state = state,
        onBack = onBack,
        onOpenRoutes = onOpenRoutes,
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun TopicDetailScreen(
    state: TopicDetailUiState,
    onBack: () -> Unit,
    onOpenRoutes: () -> Unit,
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
            tag = stringResource(R.string.content_topic_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null || state.topic == null ->
                V2ErrorState(messageRes = R.string.content_error_generic, onRetry = onRetry)
            else -> TopicDetailContent(topic = state.topic, onOpenRoutes = onOpenRoutes)
        }
    }
}

@Composable
private fun TopicDetailContent(topic: Topic, onOpenRoutes: () -> Unit) {
    val visual = topicVisual(topic.id)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(iconRes = visual.iconRes, accent = visual.accent)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = topic.title,
                    style = InteriorTypeVoice.screenTitle,
                    color = V2Palette.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SoftCard {
                Text(text = topic.summary, style = InteriorTypeVoice.body, color = V2Palette.inkPrimary)
            }
            if (topic.signals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.content_topic_signals))
                Spacer(modifier = Modifier.height(10.dp))
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        topic.signals.forEach { signal ->
                            SignalRow(text = signal, accent = visual.accent)
                        }
                    }
                }
            }
            if (topic.examples.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.content_topic_examples))
                Spacer(modifier = Modifier.height(10.dp))
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topic.examples.forEach { example ->
                            Text(
                                text = stringResource(R.string.content_topic_example_format, example),
                                style = InteriorTypeVoice.body,
                                color = V2Palette.inkPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // Conexión hacia las rutas (RF-22), fija abajo (v2-13). El único botón
        // lleno de la pantalla (regla 2); el hueco derecho es de la nube (RF-16).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
        ) {
            V2PrimaryButton(
                text = stringResource(R.string.content_topic_see_routes),
                onClick = onOpenRoutes,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(CLOUD_CLEARANCE))
        }
    }
}

/** Etiqueta de sección del detalle (v2-13). */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = InteriorTypeVoice.label, color = V2Palette.inkSecondary)
}

/** Señal con visto en el matiz del tipo (v2-13): el icono acompaña al texto, regla 3. */
@Composable
private fun SignalRow(text: String, accent: AccentPair) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccentBadge(iconRes = V2Icons.synced, accent = accent, size = 28.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = InteriorTypeVoice.body,
            color = V2Palette.inkPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Ancho reservado para que la nube (52 dp + margen) no tape la acción fija. */
internal val CLOUD_CLEARANCE = 68.dp
