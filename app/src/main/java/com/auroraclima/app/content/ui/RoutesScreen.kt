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
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/** Rutas de atención (RF-22, mockup P12 / alta-fidelidad-20): triada en una sola pantalla (decisión F5). */
@Composable
fun RoutesRoute(onBack: () -> Unit, onAgencySelected: (routeId: String, agencyId: String) -> Unit) {
    val viewModel: RoutesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    RoutesScreen(
        state = state,
        onBack = onBack,
        onAgencySelected = onAgencySelected,
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun RoutesScreen(
    state: RoutesUiState,
    onBack: () -> Unit,
    onAgencySelected: (routeId: String, agencyId: String) -> Unit,
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
            else -> RoutesContent(routes = state.routes, onAgencySelected = onAgencySelected)
        }
    }
}

@Composable
private fun RoutesContent(routes: List<Route>, onAgencySelected: (routeId: String, agencyId: String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SectionTitle(stringResource(R.string.content_routes_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_routes_subtitle),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = SteelDeep
        )
        routes.forEach { route ->
            Spacer(modifier = Modifier.height(24.dp))
            SectionTag(route.title.uppercase())
            Spacer(modifier = Modifier.height(10.dp))
            ContentRowGroup {
                route.agencies.forEachIndexed { index, agency ->
                    if (index > 0) ContentRowDivider()
                    ContentListRow(
                        title = agency.name,
                        summary = agency.role,
                        onClick = { onAgencySelected(route.id, agency.id) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}
