package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.routeVisual

/**
 * Rutas de atención (RF-22, mockup P12 / v2-14): triada en una sola pantalla
 * (decisión F5). Cada sector lleva el icono y matiz fijos del anexo
 * ([routeVisual]) y sus entidades agrupadas en una tarjeta.
 */
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
        Text(
            text = stringResource(R.string.content_routes_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_routes_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        routes.forEach { route ->
            val visual = routeVisual(route.id)
            Spacer(modifier = Modifier.height(24.dp))
            // Encabezado del sector (v2-14): insignia menor + título en el
            // trazo del acento — el color acompaña al texto (regla 3).
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(iconRes = visual.iconRes, accent = visual.accent, size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = route.title, style = InteriorTypeVoice.bodyStrong, color = visual.accent.stroke)
            }
            Spacer(modifier = Modifier.height(10.dp))
            SoftCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                route.agencies.forEachIndexed { index, agency ->
                    if (index > 0) HorizontalDivider(color = V2Palette.divider, thickness = 1.dp)
                    AgencyRow(
                        name = agency.name,
                        role = agency.role,
                        onClick = { onAgencySelected(route.id, agency.id) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

/** Fila de entidad dentro de la tarjeta del sector (v2-14). */
@Composable
private fun AgencyRow(name: String, role: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = InteriorTypeVoice.bodyStrong, color = V2Palette.inkPrimary)
            Text(
                text = role,
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RowChevron()
    }
}
