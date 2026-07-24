package com.auroraclima.app.records.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDayPicker
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.ParagraphField
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.SteelDeep
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import java.time.LocalDate

/**
 * Creación (RF-32) y edición (RF-35) de un registro; mockup P19 /
 * alta-fidelidad-29, ahora con la fila de adjuntos (RF-33) y su tercera vía
 * «Archivo» de la política ampliada (D0 §5.1.iii). Tipo con selección de radio
 * en un solo gesto (decisión F7) y validación en línea sin pérdida (criterio
 * 2). En modo edición la pantalla es la misma con el formulario prellenado;
 * solo cambian rótulo y título.
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
    onKindSelected: (EntryKind) -> Unit,
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
            .background(Mist)
            .systemBarsPadding()
    ) {
        BackHeader(
            tag = stringResource(if (state.isEditing) R.string.journal_edit_tag else R.string.journal_new_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> LoadingState()
            state.loadErrorRes != null -> ErrorState(messageRes = state.loadErrorRes, onRetry = onRetryLoad)
            else -> JournalEditorForm(
                state = state,
                onOpenDatePicker = onOpenDatePicker,
                onKindSelected = onKindSelected,
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
    onKindSelected: (EntryKind) -> Unit,
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
        SectionTitle(
            stringResource(if (state.isEditing) R.string.journal_edit_title else R.string.journal_new_title)
        )
        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.journal_date_label))
        DayField(text = state.occurredOn.toLongDayText(), onClick = onOpenDatePicker)
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.journal_kind_label))
        KindSelector(
            selected = state.kind,
            isError = state.kindErrorRes != null,
            onKindSelected = onKindSelected
        )
        FieldMessage(messageRes = state.kindErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.journal_detail_label))
        ParagraphField(
            value = state.detail,
            onValueChange = onDetailChange,
            placeholder = stringResource(R.string.journal_detail_placeholder),
            isError = state.detailErrorRes != null
        )
        FieldMessage(messageRes = state.detailErrorRes, fallbackRes = null)
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
        FieldMessage(messageRes = state.saveErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(R.string.journal_save),
            onClick = onSubmit,
            enabled = !state.isSaving,
            isLoading = state.isSaving
        )
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
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
    FieldLabel(stringResource(R.string.journal_media_label))
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
    FieldMessage(messageRes = mediaErrorRes, fallbackRes = null)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.journal_media_note),
        fontFamily = PlexSans,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = SteelDeep
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Sky),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) SteelDeep else Sky,
            modifier = Modifier.width(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (enabled) Night else SteelDeep
        )
    }
}

/** Campo de fecha de solo lectura: el valor se elige en el selector (P19). */
@Composable
private fun DayField(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CloudWhite,
        border = BorderStroke(1.dp, Sky)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontFamily = PlexSans,
                fontSize = 16.sp,
                color = Night,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = stringResource(R.string.journal_date_pick),
                tint = SteelDeep
            )
        }
    }
}

/** Tipos con radio, un solo gesto y sin menús desplegables (decisión F7). */
@Composable
private fun KindSelector(selected: EntryKind?, isError: Boolean, onKindSelected: (EntryKind) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CloudWhite,
        border = BorderStroke(1.dp, if (isError) Plum else Sky)
    ) {
        Column {
            EntryKind.entries.forEachIndexed { index, kind ->
                if (index > 0) HorizontalDivider(color = Mist, thickness = 1.dp)
                KindRow(
                    kind = kind,
                    isSelected = kind == selected,
                    onSelected = { onKindSelected(kind) }
                )
            }
        }
    }
}

@Composable
private fun KindRow(kind: EntryKind, isSelected: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelected)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Night, unselectedColor = SteelDeep)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(kind.labelRes()),
            fontFamily = PlexSans,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
            color = Night
        )
    }
}
