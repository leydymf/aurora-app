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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SecretField
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Creación de la clave personal (RF-08), mockup alta-fidelidad-10 (P06). Paso 2
 * de 3 del primer uso. Redacción de camuflaje sobre el mockup: «Tu código
 * secreto.» → «Tu clave personal.» (decisión D3/P-17: «secreto» está vedado por
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
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.access_setup_tag))
        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.access_setup_title))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.access_setup_body),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(20.dp))

        SecretField(
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

        // Reglas del mockup: las dos primeras se verifican en vivo (criterio 2);
        // la tercera es una garantía del sistema (criterio 3) y se pinta informativa.
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
        RuleRow(
            text = stringResource(R.string.access_setup_rule_storage),
            satisfied = false
        )
        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.access_setup_confirm_label))
        SecretField(
            value = state.confirmCode,
            onValueChange = onConfirmCodeChange,
            isVisible = state.isConfirmVisible,
            onToggleVisibility = onToggleConfirmVisibility,
            isError = state.confirmErrorRes != null,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            toggleDescription = stringResource(R.string.access_setup_toggle)
        )
        FieldMessage(messageRes = state.confirmErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        // Nota cálida del mockup en ciruela suave (F2: nada de alarma).
        Text(
            text = stringResource(R.string.access_setup_note),
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
            text = stringResource(R.string.access_setup_submit),
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

@Composable
private fun RuleRow(text: String, satisfied: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(
                if (satisfied) R.drawable.ic_rule_done else R.drawable.ic_rule_pending
            ),
            contentDescription = null,
            tint = if (satisfied) Night else Steel,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontFamily = PlexSans,
            fontSize = 14.sp,
            fontWeight = if (satisfied) FontWeight.SemiBold else FontWeight.Normal,
            color = if (satisfied) Night else SteelDeep
        )
    }
}
