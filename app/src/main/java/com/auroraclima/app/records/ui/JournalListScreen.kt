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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.auroraclima.app.records.domain.model.ResolvedKind

/**
 * Listado del historial (RF-34; mockup P18 / v2-20), hoy pestaña «Mi registro»
 * del hogar interno. Cada fila lleva la insignia de su tipo — el visual del
 * tema publicado, o el neutro si fue retirado (P-68) — y su contador de
 * adjuntos (RF-33). Botón de agregar a ancho
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
 *
 * Modo de selección (RF-45 criterio 1): «Seleccionar» convierte las filas en
 * casillas — ninguna marcada de entrada —, la cabecera cuenta lo marcado y el
 * botón inferior pasa a «Continuar», que entrega los ids a la pantalla de
 * exportación por la ruta. Mientras se selecciona, tocar una fila alterna la
 * marca en vez de abrir el detalle.
 */
@Composable
fun JournalListRoute(
    onBack: (() -> Unit)?,
    onAddEntry: () -> Unit,
    onEntrySelected: (Long) -> Unit,
    onExportSelected: (List<Long>) -> Unit
) {
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
        onNoticeDismissed = viewModel::onBackupNoticeDismissed,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelected = viewModel::toggleSelected,
        onCancelSelection = viewModel::cancelSelection,
        onContinueSelection = {
            val ids = viewModel.continueSelection()
            if (ids.isNotEmpty()) onExportSelected(ids)
        }
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
    onNoticeDismissed: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onToggleSelected: (Long) -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onContinueSelection: () -> Unit = {}
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
        if (state.isSelecting) {
            SelectionHeader(
                selectedCount = state.selectedIds.size,
                onCancel = onCancelSelection,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            ListHeader(
                state = state,
                onEnterSelection = onEnterSelection,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
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
                    state = state,
                    onEntrySelected = onEntrySelected,
                    onToggleSelected = onToggleSelected
                )
            }
        }
        // El botón permanece visible aun con error (agregar no depende de leer)
        // y deja libre la esquina de la nube de salida rápida (RF-16). En modo
        // de selección se vuelve «Continuar», inactivo hasta marcar algo.
        val buttonModifier = Modifier.padding(start = 24.dp, end = 24.dp + CLOUD_CLEARANCE, bottom = 16.dp)
        if (state.isSelecting) {
            V2PrimaryButton(
                text = stringResource(R.string.journal_select_continue),
                onClick = onContinueSelection,
                enabled = state.canContinue,
                modifier = buttonModifier
            )
        } else {
            V2PrimaryButton(
                text = stringResource(R.string.journal_add),
                onClick = onAddEntry,
                enabled = !state.isLoading,
                modifier = buttonModifier
            )
        }
    }
}

/** Título, chip «Todo guardado», subtítulo y la acción «Seleccionar» (RF-45). */
@Composable
private fun ListHeader(state: JournalListUiState, onEnterSelection: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.journal_subtitle),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                modifier = Modifier.weight(1f)
            )
            if (state.canSelect) {
                Spacer(modifier = Modifier.width(12.dp))
                TextAction(text = stringResource(R.string.journal_select), onClick = onEnterSelection)
            }
        }
    }
}

/** Cabecera del modo de selección: cuenta lo marcado y ofrece «Cancelar». */
@Composable
private fun SelectionHeader(selectedCount: Int, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.journal_selected_count, selectedCount, selectedCount),
                style = InteriorTypeVoice.screenTitle,
                color = V2Palette.inkPrimary,
                modifier = Modifier.weight(1f)
            )
            TextAction(text = stringResource(R.string.journal_select_cancel), onClick = onCancel)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.journal_select_hint),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
    }
}

/** Acción de texto discreta (misma forma que «Entendido» del aviso de respaldo). */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = InteriorTypeVoice.label,
        color = V2Palette.inkPrimary,
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button)
            .padding(6.dp)
    )
}

@Composable
private fun JournalListContent(
    state: JournalListUiState,
    onEntrySelected: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit
) {
    val entries = state.entries
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
                    kind = state.kindOf(entry),
                    attachmentCount = state.attachmentCounts[entry.id] ?: 0,
                    isSelected = if (state.isSelecting) entry.id in state.selectedIds else null,
                    onClick = {
                        if (state.isSelecting) onToggleSelected(entry.id) else onEntrySelected(entry.id)
                    }
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
 * porque lleva más líneas que el par título/apoyo de IconListRow. Con
 * [isSelected] no nulo (modo de selección, RF-45) el chevrón se reemplaza por
 * una casilla y toda la fila alterna la marca.
 */
@Composable
private fun EntryCard(
    entry: JournalEntry,
    kind: ResolvedKind,
    attachmentCount: Int,
    isSelected: Boolean?,
    onClick: () -> Unit
) {
    val visual = kind.visual()
    val role = if (isSelected != null) Role.Checkbox else Role.Button
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = role),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBadge(iconRes = visual.iconRes, accent = visual.accent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.occurredOn.toShortDayText() + " · " + kind.labelText(),
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
            if (isSelected != null) {
                SelectionMark(isSelected = isSelected)
            } else {
                RowChevron()
            }
        }
    }
}

/** Casilla de la fila en modo de selección; el toque lo recibe la fila entera. */
@Composable
private fun SelectionMark(isSelected: Boolean) {
    val description = stringResource(
        if (isSelected) R.string.journal_select_marked else R.string.journal_select_unmarked
    )
    Checkbox(
        checked = isSelected,
        onCheckedChange = null,
        colors = CheckboxDefaults.colors(
            checkedColor = V2Palette.inkPrimary,
            checkmarkColor = V2Palette.cardSurface,
            uncheckedColor = V2Palette.inkSecondary
        ),
        modifier = Modifier.semantics { contentDescription = description }
    )
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
