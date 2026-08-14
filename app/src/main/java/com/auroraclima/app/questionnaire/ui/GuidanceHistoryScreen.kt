package com.auroraclima.app.questionnaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.SecureSheet
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2NoticeCard
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Resultados guardados (RF-31): listado del más reciente al más antiguo,
 * detalle en hoja segura y eliminación individual con doble confirmación y
 * propagación remota (patrón de P20·b). Vive en el grafo seguro: bloqueo de
 * capturas, nube de salida y reporte de interacción por construcción (RNF-02).
 *
 * El aviso de alcance (RF-30) acompaña también aquí, FIJO fuera del scroll:
 * un resultado guardado sigue siendo un resultado. Cada tarjeta lleva la
 * franja y el texto del par de severidad de su nivel (decisión 2026-08-14) —
 * el color refuerza el nombre validado, nunca informa solo (regla 3).
 */
@Composable
fun GuidanceHistoryRoute(onBack: () -> Unit) {
    val viewModel: GuidanceHistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    GuidanceHistoryScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onOutcomeSelected = viewModel::onOutcomeSelected,
        onDetailDismissed = viewModel::onDetailDismissed,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteConfirmed = viewModel::onDeleteConfirmed
    )
}

@Composable
internal fun GuidanceHistoryScreen(
    state: GuidanceHistoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOutcomeSelected: (Long) -> Unit,
    onDetailDismissed: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        V2BackHeader(
            tag = stringResource(R.string.guidance_history_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null -> V2ErrorState(messageRes = state.errorRes, onRetry = onRetry)
            else -> HistoryContent(state = state, onOutcomeSelected = onOutcomeSelected)
        }
    }
    state.detail?.let { detail ->
        if (state.isConfirmingDelete) {
            DeleteOutcomeSheet(
                isDeleting = state.isDeleting,
                deleteErrorRes = state.deleteErrorRes,
                onConfirm = onDeleteConfirmed,
                onDismiss = onDeleteDismissed
            )
        } else {
            OutcomeDetailSheet(
                outcome = detail,
                notice = state.notice,
                onDeleteRequested = onDeleteRequested,
                onDismiss = onDetailDismissed
            )
        }
    }
}

@Composable
private fun HistoryContent(state: GuidanceHistoryUiState, onOutcomeSelected: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.guidance_history_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.guidance_history_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        // Aviso de alcance (RF-30) FIJO, fuera del área desplazable.
        state.notice?.let { notice ->
            Spacer(modifier = Modifier.height(12.dp))
            V2NoticeCard(notice)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.outcomes.isEmpty()) {
                Text(
                    text = stringResource(R.string.guidance_history_empty),
                    style = InteriorTypeVoice.body,
                    color = V2Palette.inkSecondary
                )
            } else {
                state.outcomes.forEach { outcome ->
                    OutcomeCard(outcome = outcome, onClick = { onOutcomeSelected(outcome.id) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            Spacer(modifier = Modifier.height(HISTORY_BOTTOM_CLEARANCE))
        }
    }
}

/**
 * Tarjeta de un guardado: franja y nombre del nivel en el trazo de su
 * severidad, fecha y tipos reconocidos como apoyo. La tarjeta completa es el
 * objetivo táctil.
 */
@Composable
private fun OutcomeCard(outcome: SavedOutcome, onClick: () -> Unit) {
    val accent = severityAccent(outcome.rank)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(V2Corner.card)
            .background(V2Palette.cardSurface)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(V2Corner.pill)
                .background(accent.stroke)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = outcome.label,
                style = InteriorTypeVoice.cardTitle,
                color = accent.stroke
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatShortDate(outcome),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
            if (outcome.tags.isNotEmpty()) {
                Text(
                    text = outcome.tags.joinToString(" · "),
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary
                )
            }
        }
    }
}

/**
 * Detalle en hoja segura: la tarjeta del nivel con su par de severidad, la
 * fecha completa, los tipos reconocidos y el aviso (RF-30). «Cerrar» es la
 * única acción llena (regla 2 — conservar es el camino principal); «Eliminar»
 * abre el segundo paso.
 */
@Composable
private fun OutcomeDetailSheet(
    outcome: SavedOutcome,
    notice: String?,
    onDeleteRequested: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = severityAccent(outcome.rank)
    SecureSheet(onDismissRequest = onDismiss) {
        V2SheetCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(V2Corner.card)
                    .background(accent.tint)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.guidance_result_level),
                    style = InteriorTypeVoice.label,
                    color = accent.stroke
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = outcome.label,
                    style = InteriorTypeVoice.cardTitle,
                    color = accent.stroke
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = formatLongDate(outcome),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                textAlign = TextAlign.Center
            )
            if (outcome.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.guidance_history_types,
                        outcome.tags.joinToString(", ")
                    ),
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkPrimary,
                    textAlign = TextAlign.Center
                )
            }
            notice?.let {
                Spacer(modifier = Modifier.height(12.dp))
                V2NoticeCard(it)
            }
            Spacer(modifier = Modifier.height(16.dp))
            V2PrimaryButton(text = stringResource(R.string.guidance_history_close), onClick = onDismiss)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onDeleteRequested) {
                Text(
                    text = stringResource(R.string.guidance_history_delete),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.accentNotice.stroke
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Segundo paso de la eliminación (doble confirmación, patrón P20·b):
 * «Conservar» como única acción llena y «Sí, eliminar» como texto en el rosa
 * profundo. El cuerpo dice que también se borra de la nube (RF-36 espejo).
 */
@Composable
private fun DeleteOutcomeSheet(
    isDeleting: Boolean,
    deleteErrorRes: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SecureSheet(onDismissRequest = onDismiss) {
        V2SheetCard {
            AccentBadge(iconRes = V2Icons.delete, accent = V2Palette.accentNotice)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.guidance_history_delete_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guidance_history_delete_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                textAlign = TextAlign.Center
            )
            V2FieldMessage(messageRes = deleteErrorRes, fallbackRes = null)
            Spacer(modifier = Modifier.height(18.dp))
            V2PrimaryButton(
                text = stringResource(R.string.guidance_history_delete_keep),
                onClick = onDismiss,
                enabled = !isDeleting
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(22.dp),
                    color = V2Palette.accentNotice.stroke,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(R.string.guidance_history_delete_confirm),
                        style = InteriorTypeVoice.bodyStrong,
                        color = V2Palette.accentNotice.stroke
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// Fechas en español, como el historial (JournalFormats es de records: sin
// imports entre features, cada quien formatea lo suyo — §2).
private val SPANISH = Locale("es")
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", SPANISH)
private val LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", SPANISH)

private fun formatShortDate(outcome: SavedOutcome): String =
    SHORT_DATE.format(outcome.savedAt.atZone(ZoneId.systemDefault()).toLocalDate())

private fun formatLongDate(outcome: SavedOutcome): String =
    LONG_DATE.format(outcome.savedAt.atZone(ZoneId.systemDefault()).toLocalDate())

/** Espacio final para que la lista no quede bajo la nube de la esquina (RF-16). */
private val HISTORY_BOTTOM_CLEARANCE = 88.dp
