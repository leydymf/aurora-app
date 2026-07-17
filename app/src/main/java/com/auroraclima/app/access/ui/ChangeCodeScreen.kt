package com.auroraclima.app.access.ui

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SecretField
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum

/**
 * Cambio de la clave personal (RF-09), mockup alta-fidelidad-34 (P21·b) con las
 * adaptaciones registradas en la nota de RF-09: la credencial pedida es la clave
 * vigente (no la contraseña) y la redacción de camuflaje de P-17. Los campos
 * reportan cada tecla como interacción (P-15) — el teclado es ventana propia y
 * la inactividad expulsaría a mitad de cambio.
 */
@Composable
fun ChangeCodeRoute(onCodeChanged: () -> Unit, onBack: () -> Unit) {
    val viewModel: ChangeCodeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.doneEvents.collect { onCodeChanged() }
    }
    ChangeCodeScreen(
        state = state,
        onBack = onBack,
        onCurrentCodeChange = {
            sessionManager.registerInteraction()
            viewModel.onCurrentCodeChange(it)
        },
        onNewCodeChange = {
            sessionManager.registerInteraction()
            viewModel.onNewCodeChange(it)
        },
        onToggleCurrentVisibility = viewModel::onToggleCurrentVisibility,
        onToggleNewVisibility = viewModel::onToggleNewVisibility,
        onSubmit = {
            // También el envío con la tecla «Done» del IME cuenta como interacción.
            sessionManager.registerInteraction()
            viewModel.onSubmit()
        }
    )
}

@Composable
internal fun ChangeCodeScreen(
    state: ChangeCodeUiState,
    onBack: () -> Unit,
    onCurrentCodeChange: (String) -> Unit,
    onNewCodeChange: (String) -> Unit,
    onToggleCurrentVisibility: () -> Unit,
    onToggleNewVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BackHeader(tag = stringResource(R.string.access_change_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.access_change_title))
        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.access_change_current_label))
        SecretField(
            value = state.currentCode,
            onValueChange = onCurrentCodeChange,
            isVisible = state.isCurrentVisible,
            onToggleVisibility = onToggleCurrentVisibility,
            isError = state.currentErrorRes != null,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(),
            toggleDescription = stringResource(R.string.access_setup_toggle)
        )
        FieldMessage(messageRes = state.currentErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.access_change_new_label))
        SecretField(
            value = state.newCode,
            onValueChange = onNewCodeChange,
            isVisible = state.isNewVisible,
            onToggleVisibility = onToggleNewVisibility,
            isError = false,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            toggleDescription = stringResource(R.string.access_setup_toggle)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Las mismas reglas en vivo de la creación (RF-09 criterio 2 → RF-08).
        RuleRow(
            text = stringResource(R.string.access_setup_rule_length),
            satisfied = state.hasMinLength
        )
        Spacer(modifier = Modifier.height(8.dp))
        RuleRow(
            text = stringResource(R.string.access_setup_rule_city),
            satisfied = state.cityRuleSatisfied
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Aviso del mockup en ciruela suave (F2: nada de alarma).
        Text(
            text = stringResource(R.string.access_change_note),
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Night,
            modifier = Modifier
                .fillMaxWidth()
                .background(Plum.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(R.string.access_change_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            isLoading = state.isLoading
        )
        state.generalErrorRes?.let { messageRes ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(messageRes),
                fontFamily = PlexSans,
                fontSize = 14.sp,
                color = Plum,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}
