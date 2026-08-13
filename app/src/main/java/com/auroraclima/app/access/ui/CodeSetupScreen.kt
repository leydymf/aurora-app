package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField
import com.auroraclima.app.core.ui.v2.V2StepHeader
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Creación de la clave personal (RF-08), mockup v2-07 (P06). Paso 2 de 3 del
 * primer uso. Redacción de camuflaje sobre el mockup: «Tu código secreto.» →
 * «Tu clave personal.» (decisión D3/P-17 y D1: «secreto» está vedado por
 * CamouflageResourcesTest). Campo de confirmación agregado a pedido de la
 * usuaria, como en P05. Los campos reportan cada tecla como interacción (P-15).
 */
@Composable
fun CodeSetupRoute(onCodeCreated: () -> Unit) {
    val viewModel: CodeSetupViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.continueEvents.collect { onCodeCreated() }
    }
    AuroraV2Theme(V2Voice.Interior) {
        CodeSetupScreen(
            state = state,
            onCodeChange = {
                sessionManager.registerInteraction()
                viewModel.onCodeChange(it)
            },
            onConfirmCodeChange = {
                sessionManager.registerInteraction()
                viewModel.onConfirmCodeChange(it)
            },
            onToggleCodeVisibility = viewModel::onToggleCodeVisibility,
            onToggleConfirmVisibility = viewModel::onToggleConfirmVisibility,
            onSubmit = {
                // También el envío con la tecla «Done» del IME cuenta como interacción.
                sessionManager.registerInteraction()
                viewModel.onSubmit()
            }
        )
    }
}

@Composable
internal fun CodeSetupScreen(
    state: CodeSetupUiState,
    onCodeChange: (String) -> Unit,
    onConfirmCodeChange: (String) -> Unit,
    onToggleCodeVisibility: () -> Unit,
    onToggleConfirmVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(16.dp))
        V2StepHeader(step = 2, totalSteps = 3)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.access_setup_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.access_setup_body),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta del mockup: el campo y sus reglas viven juntos; el icono de
        // cada regla cambia al cumplirse (criterio 2 de RF-08).
        SoftCard {
            V2SecretField(
                value = state.code,
                onValueChange = onCodeChange,
                isVisible = state.isCodeVisible,
                onToggleVisibility = onToggleCodeVisibility,
                isError = false,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(),
                toggleDescription = stringResource(R.string.access_setup_toggle)
            )
            Spacer(modifier = Modifier.height(14.dp))
            RuleRow(
                text = stringResource(R.string.access_setup_rule_length),
                satisfied = state.hasMinLength
            )
            Spacer(modifier = Modifier.height(8.dp))
            RuleRow(
                text = stringResource(R.string.access_setup_rule_city),
                satisfied = state.cityRuleSatisfied
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Garantía del sistema (criterio 3): se pinta informativa con la
            // llave de la librería — nunca «se cumple» tecleando.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(V2Icons.code),
                    contentDescription = null,
                    tint = V2Palette.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.access_setup_rule_storage),
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        V2FieldLabel(stringResource(R.string.access_setup_confirm_label))
        V2SecretField(
            value = state.confirmCode,
            onValueChange = onConfirmCodeChange,
            isVisible = state.isConfirmVisible,
            onToggleVisibility = onToggleConfirmVisibility,
            isError = state.confirmErrorRes != null,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            toggleDescription = stringResource(R.string.access_setup_toggle)
        )
        V2FieldMessage(messageRes = state.confirmErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        // Aviso de práctica del mockup en el rosa de avisos (regla 1; F2: nada
        // de alarma) — texto en el trazo profundo, 6.6:1 sobre el tinte.
        Text(
            text = stringResource(R.string.access_setup_note),
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentNotice.stroke,
            modifier = Modifier
                .fillMaxWidth()
                .clip(V2Corner.control)
                .background(V2Palette.accentNotice.tint)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        V2PrimaryButton(
            text = stringResource(R.string.access_setup_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            isLoading = state.isLoading
        )
        state.generalErrorRes?.let { messageRes ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(messageRes),
                style = InteriorTypeVoice.caption,
                color = V2Palette.accentNotice.stroke,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Renglón de regla en vivo del mockup P06; lo reutiliza el cambio de clave
 * (RF-09, P21·b). Al cumplirse, el icono cambia al visto en círculo y el verde
 * de «listo/cumplido» (regla 1); pendiente, círculo vacío en tinta secundaria.
 */
@Composable
internal fun RuleRow(text: String, satisfied: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(
                if (satisfied) V2Icons.synced else R.drawable.ic_rule_pending
            ),
            contentDescription = null,
            tint = if (satisfied) V2Palette.accentCare.stroke else V2Palette.inkSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = if (satisfied) InteriorTypeVoice.bodyStrong else InteriorTypeVoice.body,
            color = if (satisfied) V2Palette.inkPrimary else V2Palette.inkSecondary
        )
    }
}
