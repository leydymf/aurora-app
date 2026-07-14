package com.auroraclima.app.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
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
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Detalle de un registro (RF-34; mockup P20 / alta-fidelidad-30, sin estado de
 * sincronización ni evidencias ni editar/eliminar — RF-33/35/36/37 diferidos).
 */
@Composable
fun JournalDetailRoute(onBack: () -> Unit) {
    val viewModel: JournalDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    JournalDetailScreen(state = state, onBack = onBack, onRetry = viewModel::onRetry)
}

@Composable
internal fun JournalDetailScreen(
    state: JournalDetailUiState,
    onBack: () -> Unit,
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
            tag = stringResource(R.string.journal_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> LoadingState()
            state.errorRes != null || state.entry == null ->
                ErrorState(messageRes = state.errorRes ?: R.string.common_error_generic, onRetry = onRetry)
            else -> JournalDetailContent(entry = state.entry)
        }
    }
}

@Composable
private fun JournalDetailContent(entry: JournalEntry) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        MonoLabel(entry.occurredOn.toShortDayText())
        Spacer(modifier = Modifier.height(6.dp))
        SectionTitle(stringResource(entry.kind.labelRes()) + ".")
        Spacer(modifier = Modifier.height(20.dp))
        MonoLabel(stringResource(R.string.journal_entry_section))
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CloudWhite
        ) {
            Text(
                text = entry.detail,
                fontFamily = PlexSans,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = Night,
                modifier = Modifier.padding(16.dp)
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(88.dp))
    }
}

/** Rótulo en mono espaciado del mockup P20 (fecha y «QUÉ PASÓ»). */
@Composable
private fun MonoLabel(text: String) {
    Text(
        text = text,
        fontFamily = PlexMono,
        fontSize = 12.sp,
        letterSpacing = 0.14.em,
        color = SteelDeep
    )
}
