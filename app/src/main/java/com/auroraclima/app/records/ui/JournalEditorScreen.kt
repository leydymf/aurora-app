package com.auroraclima.app.records.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDayPicker
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2ParagraphField
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.ResolvedKind
import java.time.LocalDate

/**
 * Creación (RF-32) y edición (RF-35) de un registro; mockup P19 / v2-21, con
 * la fila de adjuntos (RF-33) y su tercera vía «Archivo» de la política
 * ampliada (D0 §5.1.iii). El tipo se elige en chips de color — los temas
 * publicados de la Guía con su mismo visual (cierre P-68) — en un solo gesto
 * (decisión F7); la validación sigue en línea y sin pérdida (criterio 2). En
 * modo edición la pantalla es la misma con el formulario prellenado; solo
 * cambia el rótulo.
 *
 * P-15: el campo de texto reporta cada tecla como interacción — el teclado es
 * una ventana propia que el observador de toques del grafo seguro no ve — y
 * las acciones del selector de fecha hacen lo mismo por idéntica razón.
 *
 * RF-33: los tres orígenes son pantallas DEL SISTEMA lanzadas por
 * ActivityResult (Photo Picker, cámara con EXTRA_OUTPUT al FileProvider
 * privado, ACTION_OPEN_DOCUMENT) — ninguna pide permisos (RNF-04). Antes de
 * cada lanzamiento se arma la ida-y-vuelta esperada (`expectSystemReturn`):
 * sin ella, el ON_STOP de la pantalla externa vaciaría la pila y la usuaria
 * jamás regresaría al formulario con su selección.
 */
@Composable
fun JournalEditorRoute(onSaved: () -> Unit, onBack: () -> Unit) {
    val viewModel: JournalEditorViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.onCaptureResult(saved)
    }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onDocumentPicked(uri?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onSaved() }
    }
    // Plomería única de lanzamiento hacia una pantalla del sistema: armar la
    // ida-y-vuelta y, si el lanzamiento falla, desarmarla — extraída para que
    // ninguna vía (ni una futura) pueda olvidar el cancelSystemReturn.
    val launchExternal: (() -> Unit) -> Unit = { launch ->
        sessionManager.expectSystemReturn()
        runCatching(launch).onFailure {
            sessionManager.cancelSystemReturn()
            viewModel.onLaunchFailed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.captureEvents.collect { uri ->
            launchExternal { takePicture.launch(Uri.parse(uri)) }
        }
    }
    JournalEditorScreen(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::onRetryLoad,
        onOpenDatePicker = viewModel::onOpenDatePicker,
        onDismissDatePicker = {
            sessionManager.registerInteraction()
            viewModel.onDismissDatePicker()
        },
        onDaySelected = {
            sessionManager.registerInteraction()
            viewModel.onDaySelected(it)
        },
        onKindSelected = viewModel::onKindSelected,
        onRetryKinds = viewModel::onRetryKinds,
        onDetailChange = {
            sessionManager.registerInteraction()
            viewModel.onDetailChange(it)
        },
        onPickGallery = {
            launchExternal {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        onPickCamera = viewModel::onRequestCapture,
        onPickDocument = {
            launchExternal {
                pickDocument.launch(AttachmentPolicy.allowedDocumentTypes.toTypedArray())
            }
        },
        onRemoveAttachment = viewModel::onRemoveAttachment,
        onSubmit = viewModel::onSubmit
    )
}

@Composable
internal fun JournalEditorScreen(
    state: JournalEditorUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onKindSelected: (ResolvedKind) -> Unit,
    onRetryKinds: () -> Unit,
    onDetailChange: (String) -> Unit,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        V2BackHeader(
            tag = stringResource(if (state.isEditing) R.string.journal_edit_tag else R.string.journal_new_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.loadErrorRes != null -> V2ErrorState(messageRes = state.loadErrorRes, onRetry = onRetryLoad)
            else -> JournalEditorForm(
                state = state,
                onOpenDatePicker = onOpenDatePicker,
                onKindSelected = onKindSelected,
                onRetryKinds = onRetryKinds,
                onDetailChange = onDetailChange,
                onPickGallery = onPickGallery,
                onPickCamera = onPickCamera,
                onPickDocument = onPickDocument,
                onRemoveAttachment = onRemoveAttachment,
                onSubmit = onSubmit
            )
        }
    }
    if (state.isPickingDate) {
        SecureDayPicker(
            initialDay = state.occurredOn,
            confirmText = stringResource(R.string.journal_date_confirm),
            dismissText = stringResource(R.string.journal_date_cancel),
            onConfirm = onDaySelected,
            onDismiss = onDismissDatePicker,
            maxDay = state.today
        )
    }
}

@Composable
private fun JournalEditorForm(
    state: JournalEditorUiState,
    onOpenDatePicker: () -> Unit,
    onKindSelected: (ResolvedKind) -> Unit,
    onRetryKinds: () -> Unit,
    onDetailChange: (String) -> Unit,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        DayField(text = state.occurredOn.toLongDayText(), onClick = onOpenDatePicker)
        Spacer(modifier = Modifier.height(20.dp))

        V2FieldLabel(stringResource(R.string.journal_kind_label))
        KindSelector(
            state = state,
            onKindSelected = onKindSelected,
            onRetryKinds = onRetryKinds
        )
        V2FieldMessage(messageRes = state.kindErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        V2FieldLabel(stringResource(R.string.journal_detail_label))
        V2ParagraphField(
            value = state.detail,
            onValueChange = onDetailChange,
            placeholder = stringResource(R.string.journal_detail_placeholder),
            isError = state.detailErrorRes != null
        )
        V2FieldMessage(messageRes = state.detailErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        MediaSection(
            attachments = state.attachments,
            canAttach = state.canAttach,
            mediaErrorRes = state.mediaErrorRes,
            onPickGallery = onPickGallery,
            onPickCamera = onPickCamera,
            onPickDocument = onPickDocument,
            onRemoveAttachment = onRemoveAttachment
        )
        V2FieldMessage(messageRes = state.saveErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(24.dp))

        V2PrimaryButton(
            text = stringResource(R.string.journal_save),
            onClick = onSubmit,
            enabled = !state.isSaving,
            isLoading = state.isSaving
        )
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(EDITOR_BOTTOM_CLEARANCE))
    }
}

/**
 * Sección «Adjuntos» del mockup P19 con las tres vías de la política aprobada
 * (D0 §5.1) y la advertencia de rastros — original en galería/archivos y
 * posible copia propia de la cámara (§5.2, riesgo residual del ADR RF-33).
 */
@Composable
private fun MediaSection(
    attachments: List<JournalAttachment>,
    canAttach: Boolean,
    mediaErrorRes: Int?,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onRemoveAttachment: (Long) -> Unit
) {
    V2FieldLabel(stringResource(R.string.journal_media_label))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaSourceButton(
            text = stringResource(R.string.journal_media_gallery),
            iconRes = R.drawable.ic_media_image,
            enabled = canAttach,
            onClick = onPickGallery,
            modifier = Modifier.weight(1f)
        )
        MediaSourceButton(
            text = stringResource(R.string.journal_media_camera),
            iconRes = R.drawable.ic_media_camera,
            enabled = canAttach,
            onClick = onPickCamera,
            modifier = Modifier.weight(1f)
        )
        MediaSourceButton(
            text = stringResource(R.string.journal_media_document),
            iconRes = R.drawable.ic_media_file,
            enabled = canAttach,
            onClick = onPickDocument,
            modifier = Modifier.weight(1f)
        )
    }
    if (attachments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        AttachmentChipGrid(
            attachments = attachments,
            onOpen = null,
            onRemove = { onRemoveAttachment(it.id) }
        )
    }
    V2FieldMessage(messageRes = mediaErrorRes, fallbackRes = null)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.journal_media_note),
        style = InteriorTypeVoice.caption,
        color = V2Palette.inkSecondary
    )
}

/** Botón de origen del mockup P19 (Galería/Cámara) más la vía «Archivo». */
@Composable
private fun MediaSourceButton(
    text: String,
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = V2Corner.control,
        border = BorderStroke(
            width = 1.2.dp,
            color = if (enabled) V2Palette.inkSecondary else V2Palette.divider
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) V2Palette.inkSecondary else V2Palette.divider,
            modifier = Modifier.width(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = InteriorTypeVoice.label,
            color = if (enabled) V2Palette.inkPrimary else V2Palette.inkSecondary
        )
    }
}

/**
 * Campo de fecha de solo lectura (v2-21): tarjeta con el calendario en verde de
 * cuidado, el rótulo adentro y el valor elegido en el selector seguro (P19).
 */
@Composable
private fun DayField(text: String, onClick: () -> Unit) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBadge(
                iconRes = V2Icons.date,
                accent = V2Palette.accentCare,
                contentDescription = stringResource(R.string.journal_date_pick)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.journal_date_label),
                    style = InteriorTypeVoice.label,
                    color = V2Palette.inkSecondary
                )
                Text(
                    text = text,
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkPrimary
                )
            }
            RowChevron()
        }
    }
}

/**
 * Selector de tipo (P-68): los chips del catálogo publicado; mientras carga,
 * un indicador discreto; sin catálogo, la nota de degradación con reintento
 * y la única opción neutra que trae `kindOptions` — registrar nunca se
 * bloquea por la red (RNF-08).
 */
@Composable
private fun KindSelector(
    state: JournalEditorUiState,
    onKindSelected: (ResolvedKind) -> Unit,
    onRetryKinds: () -> Unit
) {
    if (state.isLoadingKinds && state.kindOptions.isEmpty()) {
        CircularProgressIndicator(
            color = V2Palette.inkSecondary,
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(20.dp)
        )
        return
    }
    KindChips(
        options = state.kindOptions,
        selectedId = state.kindId,
        onKindSelected = onKindSelected
    )
    if (state.kindsUnavailable) {
        KindsUnavailableNote(onRetry = onRetryKinds, enabled = !state.isLoadingKinds)
    }
}

/** Nota de degradación sin catálogo (P-68): texto neutro y reintento en línea. */
@Composable
private fun KindsUnavailableNote(onRetry: () -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.journal_kind_unavailable),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry, enabled = enabled) {
            Text(
                text = stringResource(R.string.common_retry),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary
            )
        }
    }
}

/**
 * Tipos como chips de color (v2-21): cada uno con el matiz de su tema —
 * punto del tinte en reposo; tinte, borde y visto al elegirlo, siempre con la
 * etiqueta (regla 3). Un solo gesto y sin menús (decisión F7). FlowRow porque
 * las etiquetas varían de ancho y la V2 respeta la fuente del sistema (D5):
 * una rejilla fija las cortaría; el opt-in experimental queda acotado a este
 * selector.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(options: List<ResolvedKind>, selectedId: String?, onKindSelected: (ResolvedKind) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { kind ->
            KindChip(
                kind = kind,
                isSelected = kind.id == selectedId,
                onSelected = { onKindSelected(kind) }
            )
        }
    }
}

@Composable
private fun KindChip(kind: ResolvedKind, isSelected: Boolean, onSelected: () -> Unit) {
    val accent = kind.visual().accent
    Row(
        modifier = Modifier
            .clip(V2Corner.pill)
            .background(if (isSelected) accent.tint else V2Palette.cardSurface)
            .then(
                if (isSelected) {
                    Modifier.border(width = 1.2.dp, color = accent.stroke, shape = V2Corner.pill)
                } else {
                    Modifier
                }
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelected)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(V2Icons.synced),
                contentDescription = null,
                tint = accent.stroke,
                modifier = Modifier.size(15.dp)
            )
        } else {
            Spacer(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(accent.tint)
                    .border(width = 1.dp, color = accent.stroke.copy(alpha = 0.35f), shape = CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = kind.labelText(),
            style = if (isSelected) InteriorTypeVoice.label else InteriorTypeVoice.caption,
            color = if (isSelected) accent.stroke else V2Palette.inkPrimary
        )
    }
}

/** Espacio final para que el botón no quede bajo la nube de la esquina (RF-16). */
private val EDITOR_BOTTOM_CLEARANCE = 88.dp
