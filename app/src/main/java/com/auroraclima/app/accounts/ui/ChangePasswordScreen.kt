package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField

/**
 * Cambio de contraseña de la cuenta (RF-39; mismo lenguaje visual que el
 * cambio de clave de RF-09). Los campos reportan cada tecla como interacción
 * (P-15): el teclado es ventana propia y la inactividad expulsaría a mitad.
 * Los registros no se re-cifran — el cambio es transparente para el respaldo.
 *
 * V2 (cierre): re-estilo por extensión del sistema (decisión D6, sin mockup V2
 * propio) — mismo kit que el cambio de clave (P21·b).
 */
@Composable
fun ChangePasswordRoute(onPasswordChanged: () -> Unit, onBack: () -> Unit) {
    val viewModel: ChangePasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.doneEvents.collect { onPasswordChanged() }
    }
    ChangePasswordScreen(
        state = state,
        onBack = onBack,
        onCurrentPasswordChange = {
            sessionManager.registerInteraction()
            viewModel.onCurrentPasswordChange(it)
        },
        onNewPasswordChange = {
            sessionManager.registerInteraction()
            viewModel.onNewPasswordChange(it)
        },
        onConfirmPasswordChange = {
            sessionManager.registerInteraction()
            viewModel.onConfirmPasswordChange(it)
        },
        onToggleCurrentVisibility = viewModel::onToggleCurrentVisibility,
        onToggleNewVisibility = viewModel::onToggleNewVisibility,
        onToggleConfirmVisibility = viewModel::onToggleConfirmVisibility,
        onSubmit = {
            // También el envío con la tecla «Done» del IME cuenta como interacción.
            sessionManager.registerInteraction()
            viewModel.onSubmit()
        }
    )
}

@Composable
internal fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    onBack: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onToggleCurrentVisibility: () -> Unit,
    onToggleNewVisibility: () -> Unit,
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
        V2BackHeader(tag = stringResource(R.string.account_password_change_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_password_change_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(20.dp))

        V2FieldLabel(stringResource(R.string.account_password_change_current_label))
        V2SecretField(
            value = state.currentPassword,
            onValueChange = onCurrentPasswordChange,
            isVisible = state.isCurrentVisible,
            onToggleVisibility = onToggleCurrentVisibility,
            isError = state.currentErrorRes != null,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(),
            toggleDescription = stringResource(R.string.account_password_toggle)
        )
        V2FieldMessage(messageRes = state.currentErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        V2FieldLabel(stringResource(R.string.account_password_change_new_label))
        V2SecretField(
            value = state.newPassword,
            onValueChange = onNewPasswordChange,
            isVisible = state.isNewVisible,
            onToggleVisibility = onToggleNewVisibility,
            isError = state.newErrorRes != null,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(),
            toggleDescription = stringResource(R.string.account_password_toggle)
        )
        V2FieldMessage(
            messageRes = state.newErrorRes,
            fallbackRes = R.string.account_register_password_hint
        )
        Spacer(modifier = Modifier.height(16.dp))

        V2FieldLabel(stringResource(R.string.account_password_change_confirm_label))
        V2SecretField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            isVisible = state.isConfirmVisible,
            onToggleVisibility = onToggleConfirmVisibility,
            isError = state.confirmErrorRes != null,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            toggleDescription = stringResource(R.string.account_password_toggle)
        )
        V2FieldMessage(messageRes = state.confirmErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(24.dp))

        V2PrimaryButton(
            text = stringResource(R.string.account_password_change_submit),
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
