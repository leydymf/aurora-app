package com.auroraclima.app.content.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Líneas de atención (RF-23, mockup P14 / alta-fidelidad-22). Tocar una línea
 * abre el marcador del teléfono con ACTION_DIAL: no exige permiso alguno
 * (RNF-04), no marca por sí solo y la app no conserva rastro de la acción
 * (criterio 3, decisión F5). La llamada sale del camuflaje — marcador e
 * historial del teléfono quedan fuera de nuestra protección — y la nota al
 * pie lo advierte con la misma redacción del detalle de entidad (D4 M-2).
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
        SectionTitle(stringResource(R.string.content_lines_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_lines_subtitle),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEach { line -> LineCard(line = line, onCall = onCall) }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.content_agency_call_note),
            fontFamily = PlexSans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

/** Tarjeta de una línea (P14): número, nombre, detalle y botón de teléfono. */
@Composable
private fun LineCard(line: ContactLine, onCall: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = { onCall(line.number) })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.number,
                    fontFamily = PlexMono,
                    fontSize = 20.sp,
                    color = Night
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = line.name,
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Night
                )
                Text(
                    text = line.hours
                        ?.let { stringResource(R.string.content_lines_meta, line.description, it) }
                        ?: line.description,
                    fontFamily = PlexSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = SteelDeep
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = SteelDeep, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.content_agency_call, line.number),
                    tint = CloudWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
