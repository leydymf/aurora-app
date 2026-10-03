package com.auroraclima.app.records.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.rememberSystemLauncher
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2NoticeCard
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecondaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.model.ExportSummary
import com.auroraclima.app.records.domain.model.ExportSummaryRow
import com.auroraclima.app.records.domain.model.ResolvedKind

/**
 * Exportación selectiva (RF-45; pantalla sin mockup propio, extensión del
 * sistema V2 por la decisión D6): resumen de lo elegido, interruptor de
 * imágenes apagado de entrada, aviso de que el archivo sale del espacio
 * protegido, contraseña con confirmación y dos salidas por el selector del
 * sistema — «crear documento» (guardar) o el selector de apps (compartir) —
 * sin permisos nuevos (RNF-04). Vive en el grafo seguro: captura bloqueada y
 * nube de salida rápida por construcción (criterio 6).
 *
 * Ambas salidas son pantallas del sistema: antes de lanzarlas se arma la
 * ida-y-vuelta (`expectSystemReturn`, mismo molde que los adjuntos) para que
 * el ON_STOP no vacíe la pila y la usuaria regrese aquí. El teclado es una
 * ventana propia (P-15): cada tecla de la contraseña cuenta como interacción.
 */
@Composable
fun JournalExportRoute(onBack: () -> Unit) {
    val viewModel: JournalExportViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    val shareTitle = stringResource(R.string.journal_export_share_title)
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportPolicy.MIME_TYPE)
    ) { uri -> viewModel.onSaveTarget(uri?.toString()) }
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onShareReturned()
    }
    val launchExternal = rememberSystemLauncher(onLaunchFailed = viewModel::onLaunchFailed)
    LaunchedEffect(viewModel) {
        viewModel.launchEvents.collect { event ->
            when (event) {
                is ExportLaunch.Save -> launchExternal { createDocument.launch(event.suggestedName) }
                is ExportLaunch.Share -> launchExternal { share.launch(shareIntent(Uri.parse(event.uri), shareTitle)) }
            }
        }
    }
    JournalExportScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onIncludeImagesChanged = viewModel::onIncludeImagesChanged,
        onPasswordChanged = {
            sessionManager.registerInteraction()
            viewModel.onPasswordChanged(it)
        },
        onConfirmationChanged = {
            sessionManager.registerInteraction()
            viewModel.onConfirmationChanged(it)
        },
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSave = viewModel::onSaveRequested,
        onShare = viewModel::onShareRequested
    )
}

/** ACTION_SEND con permiso de lectura puntual sobre el temporal del FileProvider; el selector es del sistema. */
private fun shareIntent(uri: Uri, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ExportPolicy.MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title)
}

@Composable
internal fun JournalExportScreen(
    state: JournalExportUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onIncludeImagesChanged: (Boolean) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
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
            state.summary == null -> V2ErrorState(
                messageRes = state.errorRes ?: R.string.common_error_generic,
                onRetry = onRetry
            )
            else -> ExportForm(
                state = state,
                summary = state.summary,
                onBack = onBack,
                onIncludeImagesChanged = onIncludeImagesChanged,
                onPasswordChanged = onPasswordChanged,
                onConfirmationChanged = onConfirmationChanged,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onSave = onSave,
                onShare = onShare
            )
        }
    }
}

@Composable
private fun ExportForm(
    state: JournalExportUiState,
    summary: ExportSummary,
    onBack: () -> Unit,
    onIncludeImagesChanged: (Boolean) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.journal_export_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.journal_export_subtitle),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        SummaryCard(summary = summary, kindOf = state::kindOf)
        Spacer(modifier = Modifier.height(12.dp))
        IncludeImagesRow(
            checked = state.includeImages,
            enabled = state.canExport,
            onCheckedChange = onIncludeImagesChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        V2NoticeCard(text = stringResource(R.string.journal_export_notice))
        Spacer(modifier = Modifier.height(20.dp))
        if (state.isSaved) {
            SavedState(onBack = onBack)
        } else {
            PasswordFields(
                state = state,
                onPasswordChanged = onPasswordChanged,
                onConfirmationChanged = onConfirmationChanged,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onSave = onSave
            )
            Spacer(modifier = Modifier.height(20.dp))
            ExportActions(state = state, onSave = onSave, onShare = onShare)
        }
        Spacer(modifier = Modifier.height(CLOUD_CLEARANCE_BOTTOM))
    }
}

/** Resumen (criterio 2): cuántos registros, cuáles (fecha · tipo vivo) y cuántas imágenes podrían ir. */
@Composable
private fun SummaryCard(summary: ExportSummary, kindOf: (ExportSummaryRow) -> ResolvedKind) {
    SoftCard {
        Text(
            text = pluralStringResource(R.plurals.journal_export_summary_count, summary.entryCount, summary.entryCount),
            style = InteriorTypeVoice.cardTitle,
            color = V2Palette.inkPrimary
        )
        summary.rows.forEach { row ->
            Spacer(modifier = Modifier.height(10.dp))
            val kind = kindOf(row)
            val visual = kind.visual()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(iconRes = visual.iconRes, accent = visual.accent)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = row.occurredOn.toShortDayText() + " · " + kind.labelText(),
                    style = InteriorTypeVoice.body,
                    color = V2Palette.inkPrimary
                )
            }
        }
        if (summary.imageCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.journal_export_images_count,
                    summary.imageCount,
                    summary.imageCount
                ),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary
            )
        }
    }
}

/** Interruptor de imágenes (criterio 3): apagado por defecto; documentos y audios nunca se incrustan. */
@Composable
private fun IncludeImagesRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SoftCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.journal_export_include_images),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.inkPrimary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = V2Palette.cardSurface,
                    checkedTrackColor = V2Palette.inkPrimary,
                    uncheckedThumbColor = V2Palette.inkSecondary,
                    uncheckedTrackColor = V2Palette.cardSurface,
                    uncheckedBorderColor = V2Palette.inkSecondary
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journal_export_include_note),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
    }
}

/** Contraseña del archivo con confirmación (criterio 4); errores solo tras intentar exportar. */
@Composable
private fun PasswordFields(
    state: JournalExportUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSave: () -> Unit
) {
    val toggleDescription = stringResource(R.string.journal_export_password_toggle)
    V2FieldLabel(text = stringResource(R.string.journal_export_password_label))
    Spacer(modifier = Modifier.height(6.dp))
    V2SecretField(
        value = state.password,
        onValueChange = onPasswordChanged,
        isVisible = state.isPasswordVisible,
        onToggleVisibility = onTogglePasswordVisibility,
        isError = state.passwordErrorRes != null,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(),
        toggleDescription = toggleDescription
    )
    Spacer(modifier = Modifier.height(12.dp))
    V2FieldLabel(text = stringResource(R.string.journal_export_password_confirm))
    Spacer(modifier = Modifier.height(6.dp))
    V2SecretField(
        value = state.confirmation,
        onValueChange = onConfirmationChanged,
        isVisible = state.isPasswordVisible,
        onToggleVisibility = onTogglePasswordVisibility,
        isError = state.passwordErrorRes != null,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { if (state.canExport) onSave() }),
        toggleDescription = toggleDescription
    )
    Spacer(modifier = Modifier.height(6.dp))
    V2FieldMessage(messageRes = state.passwordErrorRes, fallbackRes = R.string.journal_export_password_help)
}

/** Las dos salidas (criterio 5); durante la generación ambas quedan inactivas y la principal gira. */
@Composable
private fun ExportActions(state: JournalExportUiState, onSave: () -> Unit, onShare: () -> Unit) {
    if (state.errorRes != null) {
        V2FieldMessage(messageRes = state.errorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(10.dp))
    }
    if (state.isExporting) {
        Text(
            text = stringResource(R.string.journal_export_preparing),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
    V2PrimaryButton(
        text = stringResource(R.string.journal_export_save),
        onClick = onSave,
        enabled = state.canExport,
        isLoading = state.isExporting,
        modifier = Modifier.padding(end = CLOUD_CLEARANCE_END)
    )
    Spacer(modifier = Modifier.height(10.dp))
    V2SecondaryButton(
        text = stringResource(R.string.journal_export_share),
        onClick = onShare,
        enabled = state.canExport,
        modifier = Modifier.padding(end = CLOUD_CLEARANCE_END)
    )
}

@Composable
private fun SavedState(onBack: () -> Unit) {
    SoftCard {
        Text(
            text = stringResource(R.string.journal_export_saved),
            style = InteriorTypeVoice.bodyStrong,
            color = V2Palette.inkPrimary
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    V2PrimaryButton(
        text = stringResource(R.string.journal_export_done),
        onClick = onBack,
        modifier = Modifier.padding(end = CLOUD_CLEARANCE_END)
    )
}

/** Espacio para que la nube de salida rápida (RF-16) no tape los botones. */
private val CLOUD_CLEARANCE_END = 68.dp
private val CLOUD_CLEARANCE_BOTTOM = 88.dp
