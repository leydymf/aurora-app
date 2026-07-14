package com.auroraclima.app.records.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
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
import com.auroraclima.app.records.domain.model.EntryKind
import java.time.LocalDate

/**
 * Creación de un registro (RF-32; mockup P19 / alta-fidelidad-29, sin la fila
 * de evidencias — RF-33 queda fuera del MVP). Tipo con selección de radio en
 * un solo gesto (decisión F7) y validación en línea sin pérdida (criterio 2).
 *
 * P-15: el campo de texto reporta cada tecla como interacción — el teclado es
 * una ventana propia que el observador de toques del grafo seguro no ve — y
 * las acciones del selector de fecha hacen lo mismo por idéntica razón.
 */
@Composable
fun JournalEditorRoute(onSaved: () -> Unit, onBack: () -> Unit) {
    val viewModel: JournalEditorViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onSaved() }
    }
    JournalEditorScreen(
        state = state,
        onBack = onBack,
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
        onSubmit = viewModel::onSubmit
    )
}

@Composable
internal fun JournalEditorScreen(
    state: JournalEditorUiState,
    onBack: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onKindSelected: (EntryKind) -> Unit,
    onDetailChange: (String) -> Unit,
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
            tag = stringResource(R.string.journal_new_tag),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.journal_new_title))
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
    if (state.isPickingDate) {
        SecureDayPicker(
            initialDay = state.occurredOn,
            confirmText = stringResource(R.string.journal_date_confirm),
            dismissText = stringResource(R.string.journal_date_cancel),
            onConfirm = onDaySelected,
            onDismiss = onDismissDatePicker
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
