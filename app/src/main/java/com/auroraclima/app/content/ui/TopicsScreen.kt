package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/** Portada del módulo de información (RF-21, mockup P10 / alta-fidelidad-18). */
@Composable
fun TopicsRoute(onBack: () -> Unit, onTopicSelected: (String) -> Unit, onOpenRoutes: () -> Unit) {
    val viewModel: TopicsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    TopicsScreen(
        state = state,
        onBack = onBack,
        onTopicSelected = onTopicSelected,
        onOpenRoutes = onOpenRoutes,
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun TopicsScreen(
    state: TopicsUiState,
    onBack: () -> Unit,
    onTopicSelected: (String) -> Unit,
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
            tag = stringResource(R.string.content_info_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> ContentLoadingState()
            state.errorRes != null -> ContentErrorState(onRetry = onRetry)
            else -> TopicsContent(
                state = state,
                onTopicSelected = onTopicSelected,
                onOpenRoutes = onOpenRoutes
            )
        }
    }
}

@Composable
private fun TopicsContent(state: TopicsUiState, onTopicSelected: (String) -> Unit, onOpenRoutes: () -> Unit) {
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
        Spacer(modifier = Modifier.height(16.dp))
        // Acceso directo del mockup P10 a la triada de sectores (RF-22). El acceso
        // a las líneas de contacto directo llegará con RF-23 (Día 4, Sesión 3).
        RoutesShortcutCard(onClick = onOpenRoutes)
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

@Composable
private fun RoutesShortcutCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = Sky, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = null,
                    tint = SteelDeep,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.content_topics_routes_button),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Night,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}
