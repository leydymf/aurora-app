package com.auroraclima.app.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureSheet
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecondaryButton
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Detalle de un registro (RF-34; mockup P20 / v2-22, sin estado de
 * sincronización — la sync es silenciosa, P-45b) con la sección de adjuntos
 * (RF-33: chips por familia, tocar abre el visor interno y la nota recuerda
 * que solo se ven aquí), «Editar» (RF-35) y «Eliminar» en dos pasos (RF-36).
 *
 * V2 (S4): la confirmación de P20·b pasa de diálogo centrado a HOJA INFERIOR
 * con «Conservar» como acción principal (v2-22) — mismo mecanismo de dos
 * pasos sobre el mismo estado (`isConfirmingDelete`) y misma ventana segura:
 * [SecureSheet] es la variante de hoja de SecureDialog (RNF-02, regla A1).
 *
 * P-15: los botones de la hoja registran interacción — la hoja es una ventana
 * propia que el observador de toques del grafo seguro no ve, igual que el
 * selector de fecha del editor.
 */
@Composable
fun JournalDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenAttachment: (Long) -> Unit
) {
    val viewModel: JournalDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collect { onDeleted() }
    }
    JournalDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onEdit = { viewModel.entryId?.let(onEdit) },
        onOpenAttachment = onOpenAttachment,
        onRequestDelete = viewModel::onRequestDelete,
        onDismissDelete = {
            sessionManager.registerInteraction()
            viewModel.onDismissDelete()
        },
        onConfirmDelete = {
            sessionManager.registerInteraction()
            viewModel.onConfirmDelete()
        }
    )
}

@Composable
internal fun JournalDetailScreen(
    state: JournalDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        V2BackHeader(
            tag = stringResource(R.string.journal_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null || state.entry == null ->
                V2ErrorState(messageRes = state.errorRes ?: R.string.common_error_generic, onRetry = onRetry)
            else -> JournalDetailContent(
                entry = state.entry,
                attachments = state.attachments,
                hasAudio = state.hasAudio,
                onOpenAttachment = onOpenAttachment,
                onEdit = onEdit,
                onRequestDelete = onRequestDelete
            )
        }
    }
    if (state.isConfirmingDelete) {
        DeleteConfirmSheet(
            isDeleting = state.isDeleting,
            deleteErrorRes = state.deleteErrorRes,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete
        )
    }
}

@Composable
private fun JournalDetailContent(
    entry: JournalEntry,
    attachments: List<JournalAttachment>,
    hasAudio: Boolean,
    onOpenAttachment: (Long) -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        // Cabecera de v2-22: insignia del tipo, fecha y el tipo como título.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val visual = entry.kind.visual()
            AccentBadge(iconRes = visual.iconRes, accent = visual.accent)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = entry.occurredOn.toLongDayText(),
                    style = InteriorTypeVoice.label,
                    color = V2Palette.inkSecondary
                )
                Text(
                    text = stringResource(entry.kind.labelRes()),
                    style = InteriorTypeVoice.screenTitle,
                    color = V2Palette.inkPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.journal_entry_section),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoftCard {
            Text(
                text = entry.detail,
                style = InteriorTypeVoice.body,
                color = V2Palette.inkPrimary
            )
        }
        if (attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            AttachmentSection(
                attachments = attachments,
                hasAudio = hasAudio,
                onOpenAttachment = onOpenAttachment
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        V2SecondaryButton(text = stringResource(R.string.journal_detail_edit), onClick = onEdit)
        Spacer(modifier = Modifier.height(4.dp))
        // Eliminar en el rosa profundo de avisos (regla 1: irreversible se
        // anuncia con texto, sin rojo de alarma — decisión F2).
        TextButton(
            onClick = onRequestDelete,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.journal_detail_delete),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.accentNotice.stroke
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(88.dp))
    }
}

/** Sección «Adjuntos · N» del mockup P20 (RF-33): chips que abren el visor interno. */
@Composable
private fun AttachmentSection(
    attachments: List<JournalAttachment>,
    hasAudio: Boolean,
    onOpenAttachment: (Long) -> Unit
) {
    Text(
        text = pluralStringResource(R.plurals.journal_media_count, attachments.size, attachments.size),
        style = InteriorTypeVoice.label,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    AttachmentChipGrid(
        attachments = attachments,
        onOpen = { onOpenAttachment(it.id) },
        onRemove = null
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.journal_media_only_here),
        style = InteriorTypeVoice.caption,
        color = V2Palette.inkSecondary
    )
    if (hasAudio) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journal_media_audio_note),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
    }
}

/**
 * Confirmación en dos pasos de la eliminación (RF-36 criterio 1; mockup P20·b,
 * v2-22): hoja inferior segura con la papelera en rosa de aviso, «Conservar»
 * como ÚNICA acción llena (regla 2 — conservar es el camino principal) y
 * «Sí, eliminar» como texto en el rosa profundo.
 */
@Composable
private fun DeleteConfirmSheet(
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
                text = stringResource(R.string.journal_delete_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.journal_delete_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                textAlign = TextAlign.Center
            )
            V2FieldMessage(messageRes = deleteErrorRes, fallbackRes = null)
            Spacer(modifier = Modifier.height(18.dp))
            V2PrimaryButton(
                text = stringResource(R.string.journal_delete_keep),
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
                        text = stringResource(R.string.journal_delete_confirm),
                        style = InteriorTypeVoice.bodyStrong,
                        color = V2Palette.accentNotice.stroke
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
