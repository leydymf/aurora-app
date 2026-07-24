package com.auroraclima.app.records.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDialog
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.DestructiveButton
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.SteelDeep
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry

/**
 * Detalle de un registro (RF-34; mockup P20 / alta-fidelidad-30, sin estado de
 * sincronización — la sync es silenciosa, P-45b) con la sección de adjuntos
 * (RF-33: chips por familia, tocar abre el visor interno de imagen/PDF y la
 * nota recuerda que solo se ven aquí), «Editar» (RF-35) y «Eliminar» en dos
 * pasos (RF-36; el diálogo de P20·b va en [SecureDialog], única ventana propia
 * permitida — RNF-02). Quitar adjuntos se hace desde «Editar», como el resto
 * del contenido.
 *
 * P-15: los botones del diálogo registran interacción — el diálogo es una
 * ventana propia que el observador de toques del grafo seguro no ve, igual que
 * el selector de fecha del editor.
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
        DeleteConfirmDialog(
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
        if (attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            AttachmentSection(
                attachments = attachments,
                hasAudio = hasAudio,
                onOpenAttachment = onOpenAttachment
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedActionButton(text = stringResource(R.string.journal_detail_edit), onClick = onEdit)
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onRequestDelete,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.journal_detail_delete),
                fontFamily = PlexSans,
                fontSize = 15.sp,
                color = Plum
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(88.dp))
    }
}

/** Sección «ADJUNTOS · N» del mockup P20 (RF-33): chips que abren el visor interno. */
@Composable
private fun AttachmentSection(
    attachments: List<JournalAttachment>,
    hasAudio: Boolean,
    onOpenAttachment: (Long) -> Unit
) {
    MonoLabel(
        pluralStringResource(R.plurals.journal_media_count, attachments.size, attachments.size)
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
        fontFamily = PlexSans,
        fontSize = 12.sp,
        color = SteelDeep
    )
    if (hasAudio) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journal_media_audio_note),
            fontFamily = PlexSans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = SteelDeep
        )
    }
}

/** Confirmación en dos pasos de la eliminación (RF-36 criterio 1; mockup P20·b). */
@Composable
private fun DeleteConfirmDialog(
    isDeleting: Boolean,
    deleteErrorRes: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SecureDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CloudWhite) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    text = stringResource(R.string.journal_delete_title),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Plum
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.journal_delete_body),
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SteelDeep
                )
                FieldMessage(messageRes = deleteErrorRes, fallbackRes = null)
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedActionButton(text = stringResource(R.string.journal_delete_keep), onClick = onDismiss)
                Spacer(modifier = Modifier.height(10.dp))
                DestructiveButton(
                    text = stringResource(R.string.journal_delete_confirm),
                    onClick = onConfirm,
                    enabled = true,
                    isLoading = isDeleting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Botón secundario delineado del mockup P20/P20·b («Editar», «Conservar»). */
@Composable
private fun OutlinedActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Sky)
    ) {
        Text(
            text = text,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Night
        )
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
