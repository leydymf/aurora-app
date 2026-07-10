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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Sky

/** Detalle de un tipo (RF-21, mockup P11 / alta-fidelidad-19). */
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
            .background(Mist)
            .systemBarsPadding()
    ) {
        ContentBackHeader(
            tag = stringResource(R.string.content_topic_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> ContentLoadingState()
            state.errorRes != null || state.topic == null -> ContentErrorState(onRetry = onRetry)
            else -> TopicDetailContent(topic = state.topic, onOpenRoutes = onOpenRoutes)
        }
    }
}

@Composable
private fun TopicDetailContent(topic: Topic, onOpenRoutes: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SectionTitle(topic.title)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = topic.summary,
            fontFamily = Marcellus,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = Night
        )
        if (topic.signals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTag(stringResource(R.string.content_topic_signals))
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                topic.signals.forEach { signal -> BulletRow(signal) }
            }
        }
        if (topic.examples.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTag(stringResource(R.string.content_topic_examples))
            Spacer(modifier = Modifier.height(12.dp))
            ExamplesCard(examples = topic.examples)
        }
        Spacer(modifier = Modifier.height(24.dp))
        SeeRoutesButton(onClick = onOpenRoutes)
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

@Composable
private fun ExamplesCard(examples: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            examples.forEach { example ->
                Text(
                    text = stringResource(R.string.content_topic_example_format, example),
                    fontFamily = PlexSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Night
                )
            }
        }
    }
}

/** Conexión del mockup P11 hacia la triada de sectores (RF-22). */
@Composable
private fun SeeRoutesButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sky,
            contentColor = Night
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.content_topic_see_routes),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
