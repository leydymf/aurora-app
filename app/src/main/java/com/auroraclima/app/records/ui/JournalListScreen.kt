package com.auroraclima.app.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Listado del historial (RF-34; mockup P18 / alta-fidelidad-28, sin los chips
 * de sincronización — RF-37/38 quedan para la siguiente sesión — ni contadores
 * de evidencias, RF-33 fuera del MVP). Botón de agregar a ancho completo, no
 * FAB: la esquina inferior derecha pertenece a la salida rápida (decisión F7).
 *
 * refresh() corre en cada entrada en composición: al volver del editor la
 * ruta reingresa y el registro recién guardado aparece de inmediato.
 */
@Composable
fun JournalListRoute(onBack: () -> Unit, onAddEntry: () -> Unit, onEntrySelected: (Long) -> Unit) {
    val viewModel: JournalListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    JournalListScreen(
        state = state,
        onBack = onBack,
        onAddEntry = onAddEntry,
        onEntrySelected = onEntrySelected,
        onRetry = viewModel::refresh,
        onNoticeDismissed = viewModel::onBackupNoticeDismissed
    )
}

@Composable
internal fun JournalListScreen(
    state: JournalListUiState,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEntrySelected: (Long) -> Unit,
    onRetry: () -> Unit,
    onNoticeDismissed: () -> Unit = {},
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
        // Aviso de respaldo reiniciado (RF-39): genérico, cerrable y sin
        // tecnicismos — lo anterior no pudo recuperarse; lo nuevo sigue normal.
        if (state.showsBackupNotice) {
            BackupNoticeCard(
                onDismiss = onNoticeDismissed,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> LoadingState()
                state.errorRes != null -> ErrorState(messageRes = state.errorRes, onRetry = onRetry)
                else -> JournalListContent(
                    entries = state.entries,
                    attachmentCounts = state.attachmentCounts,
                    onEntrySelected = onEntrySelected
                )
            }
        }
        // El botón permanece visible aun con error: agregar no depende de leer.
        PrimaryButton(
            text = stringResource(R.string.journal_add),
            onClick = onAddEntry,
            enabled = !state.isLoading,
            isLoading = false,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun JournalListContent(
    entries: List<JournalEntry>,
    attachmentCounts: Map<Long, Int>,
    onEntrySelected: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.journal_title))
                Spacer(modifier = Modifier.height(16.dp))
                if (entries.isEmpty()) EmptyJournal()
            }
        }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                EntryCard(
                    entry = entry,
                    attachmentCount = attachmentCounts[entry.id] ?: 0,
                    onClick = { onEntrySelected(entry.id) }
                )
                if (index < entries.lastIndex) Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** Fila del mockup P18: fecha en mono (con «· N ADJUNTOS» si los hay, RF-33), tipo y primera línea. */
@Composable
private fun EntryCard(entry: JournalEntry, attachmentCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val dateLine = if (attachmentCount > 0) {
                    entry.occurredOn.toShortDayText() + " · " +
                        pluralStringResource(R.plurals.journal_media_count, attachmentCount, attachmentCount)
                } else {
                    entry.occurredOn.toShortDayText()
                }
                Text(
                    text = dateLine,
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    letterSpacing = 0.12.em,
                    color = SteelDeep
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(entry.kind.labelRes()),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Night
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.detail,
                    fontFamily = PlexSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = SteelDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}

/** Tarjeta silenciosa del aviso (RF-39): un texto y «Entendido», nada más. */
@Composable
private fun BackupNoticeCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CloudWhite
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.journal_backup_notice),
                fontFamily = PlexSans,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = SteelDeep
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.journal_backup_notice_dismiss),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Night,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun EmptyJournal() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_empty_title),
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Night
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journal_empty_body),
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = SteelDeep
        )
    }
}
