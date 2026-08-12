package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette

/**
 * Líneas de atención (RF-23, mockup P14 / v2-16). Tocar una línea abre el
 * marcador del teléfono con ACTION_DIAL: no exige permiso alguno (RNF-04), no
 * marca por sí solo y la app no conserva rastro de la acción (criterio 3,
 * decisión F5). La llamada sale del camuflaje — marcador e historial del
 * teléfono quedan fuera de nuestra protección — y la nota al pie lo advierte
 * con la misma redacción del detalle de entidad (D4 M-2).
 */
@Composable
fun LinesRoute(onBack: () -> Unit) {
    val viewModel: LinesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LinesScreen(
        state = state,
        onBack = onBack,
        onCall = { number -> dialNumber(context, number) },
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun LinesScreen(
    state: LinesUiState,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
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
            else -> LinesContent(lines = state.lines, onCall = onCall)
        }
    }
}

@Composable
private fun LinesContent(lines: List<ContactLine>, onCall: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_lines_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_lines_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            lines.forEach { line -> LineCard(line = line, onCall = onCall) }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.content_agency_call_note),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

/**
 * Tarjeta de una línea (v2-16): número protagonista, nombre, detalle y la
 * insignia verde de llamar. Toda la tarjeta es el destino táctil; la insignia
 * es refuerzo visual (regla 3) y el toque anuncia la llamada por accesibilidad.
 */
@Composable
private fun LineCard(line: ContactLine, onCall: (String) -> Unit) {
    val callLabel = stringResource(R.string.content_agency_call, line.number)
    SoftCard(
        modifier = Modifier
            .clip(V2Corner.card)
            .clickable(onClick = { onCall(line.number) }, onClickLabel = callLabel, role = Role.Button)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = line.number, style = InteriorTypeVoice.numberLarge, color = V2Palette.inkPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = line.name, style = InteriorTypeVoice.bodyStrong, color = V2Palette.inkPrimary)
                Text(
                    text = line.hours
                        ?.let { stringResource(R.string.content_lines_meta, line.description, it) }
                        ?: line.description,
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AccentBadge(iconRes = V2Icons.call, accent = V2Palette.accentCare)
        }
    }
}
