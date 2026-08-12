package com.auroraclima.app.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentChip
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Listado del historial (RF-34; mockup P18 / v2-20), hoy pestaña «Mi registro»
 * del hogar interno. Cada fila lleva la insignia de su tipo (espejo del mapeo
 * D2 de la Guía) y su contador de adjuntos (RF-33). Botón de agregar a ancho
 * casi completo con la esquina reservada a la salida rápida (decisión F7).
 *
 * El chip «Todo guardado» (v2-20) se muestra cuando la lista cargó sin error:
 * enuncia el guardado LOCAL protegido — lo único que la UI conoce. La
 * sincronización con la nube es silenciosa y de mejor esfuerzo por diseño
 * (RF-37, P-45b): no expone estado, así que el chip no lo inventa; un estado
 * agregado «en espera» exigiría abrir la capa de datos (fuera del alcance
 * solo-ui de la V2, anotado en el README de mockups-v2 §2.5).
 *
 * refresh() corre en cada entrada en composición: al volver del editor la
 * ruta reingresa y el registro recién guardado aparece de inmediato.
 */
@Composable
fun JournalListRoute(onBack: (() -> Unit)?, onAddEntry: () -> Unit, onEntrySelected: (Long) -> Unit) {
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
    onBack: (() -> Unit)?,
    onAddEntry: () -> Unit,
    onEntrySelected: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onNoticeDismissed: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        if (onBack != null) {
            V2BackHeader(
                tag = stringResource(R.string.journal_tag),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.journal_title),
                style = InteriorTypeVoice.screenTitle,
                color = V2Palette.inkPrimary,
                modifier = Modifier.weight(1f)
            )
            // Guardado local en verde de «listo» (regla 1), con icono y texto:
            // el color nunca informa solo (regla 3).
            if (!state.isLoading && state.errorRes == null) {
                AccentChip(
                    text = stringResource(R.string.journal_saved_chip),
                    accent = V2Palette.accentCare,
                    iconRes = V2Icons.synced
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.journal_subtitle),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        // Aviso de respaldo reiniciado (RF-39): genérico, cerrable y sin
        // tecnicismos — lo anterior no pudo recuperarse; lo nuevo sigue normal.
        if (state.showsBackupNotice) {
            BackupNoticeCard(
                onDismiss = onNoticeDismissed,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> V2LoadingState()
                state.errorRes != null -> V2ErrorState(messageRes = state.errorRes, onRetry = onRetry)
                else -> JournalListContent(
                    entries = state.entries,
                    attachmentCounts = state.attachmentCounts,
                    onEntrySelected = onEntrySelected
                )
            }
        }
        // El botón permanece visible aun con error (agregar no depende de leer)
        // y deja libre la esquina de la nube de salida rápida (RF-16).
        V2PrimaryButton(
            text = stringResource(R.string.journal_add),
            onClick = onAddEntry,
            enabled = !state.isLoading,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp + CLOUD_CLEARANCE, bottom = 16.dp)
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
                if (index < entries.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * Fila de v2-20: insignia del tipo, «fecha · tipo» arriba, primera línea del
 * texto y el contador de adjuntos con su icono (RF-33). Compuesta a mano
 * porque lleva más líneas que el par título/apoyo de IconListRow.
 */
@Composable
private fun EntryCard(entry: JournalEntry, attachmentCount: Int, onClick: () -> Unit) {
    val visual = entry.kind.visual()
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBadge(iconRes = visual.iconRes, accent = visual.accent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.occurredOn.toShortDayText() + " · " + stringResource(entry.kind.labelRes()),
                    style = InteriorTypeVoice.label,
                    color = V2Palette.inkSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.detail,
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachmentCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(V2Icons.media),
                            contentDescription = null,
                            tint = V2Palette.inkSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.journal_media_count,
                                attachmentCount,
                                attachmentCount
                            ),
                            style = InteriorTypeVoice.label,
                            color = V2Palette.inkSecondary
                        )
                    }
                }
            }
            RowChevron()
        }
    }
}

/** Tarjeta silenciosa del aviso (RF-39): un texto y «Entendido», nada más. */
@Composable
private fun BackupNoticeCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.journal_backup_notice),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.journal_backup_notice_dismiss),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkPrimary,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss)
                .padding(6.dp)
        )
    }
}

@Composable
private fun EmptyJournal() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_empty_title),
            style = InteriorTypeVoice.cardTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journal_empty_body),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
    }
}

/** Ancho reservado para que la nube (52 dp + margen) no tape el botón fijo (RF-16). */
private val CLOUD_CLEARANCE = 68.dp
