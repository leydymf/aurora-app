package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PlainField
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField
import com.auroraclima.app.core.ui.v2.V2StepHeader
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Creación de cuenta (RF-07), mockup v2-06 (P05). Paso 1 de 3 del primer uso.
 * Los campos reportan cada tecla como interacción (P-15): el teclado es una
 * ventana propia sin eventos de puntero sobre la app y el temporizador de
 * inactividad (RNF-03) expiraría mientras la usuaria escribe.
 *
 * RF-39: el enlace «ya tengo una cuenta» abre el inicio de sesión dentro del
 * mismo flujo de activación — es la puerta de la restauración multi-dispositivo
 * (dispositivo nuevo: activación → login → código nuevo).
 */
@Composable
fun CreateAccountRoute(onAccountCreated: () -> Unit, onOpenLogin: () -> Unit) {
    val viewModel: CreateAccountViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.continueEvents.collect { onAccountCreated() }
    }
    AuroraV2Theme(V2Voice.Interior) {
        CreateAccountScreen(
            state = state,
            onOpenLogin = onOpenLogin,
            onEmailChange = {
                sessionManager.registerInteraction()
                viewModel.onEmailChange(it)
            },
            onPasswordChange = {
                sessionManager.registerInteraction()
                viewModel.onPasswordChange(it)
            },
            onConfirmPasswordChange = {
                sessionManager.registerInteraction()
                viewModel.onConfirmPasswordChange(it)
            },
            onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
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
internal fun CreateAccountScreen(
    state: CreateAccountUiState,
    onOpenLogin: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
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
        V2StepHeader(step = 1, totalSteps = 3)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_register_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.account_register_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        SoftCard {
            V2FieldLabel(stringResource(R.string.account_register_email_label))
            V2PlainField(
                value = state.email,
                onValueChange = onEmailChange,
                isError = state.emailErrorRes != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            V2FieldMessage(messageRes = state.emailErrorRes, fallbackRes = null)
            Spacer(modifier = Modifier.height(16.dp))

            V2FieldLabel(stringResource(R.string.account_register_password_label))
            V2SecretField(
                value = state.password,
                onValueChange = onPasswordChange,
                isVisible = state.isPasswordVisible,
                onToggleVisibility = onTogglePasswordVisibility,
                isError = state.passwordErrorRes != null,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(),
                toggleDescription = stringResource(R.string.account_password_toggle)
            )
            V2FieldMessage(
                messageRes = state.passwordErrorRes,
                fallbackRes = R.string.account_register_password_hint
            )
            Spacer(modifier = Modifier.height(16.dp))

            V2FieldLabel(stringResource(R.string.account_register_confirm_label))
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
        }
        Spacer(modifier = Modifier.height(24.dp))

        V2PrimaryButton(
            text = stringResource(R.string.account_register_submit),
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
        Spacer(modifier = Modifier.height(16.dp))
        // RF-39: puerta hacia la restauración en un dispositivo nuevo.
        Text(
            text = stringResource(R.string.account_register_login_link),
            style = InteriorTypeVoice.bodyStrong,
            color = V2Palette.inkPrimary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onOpenLogin)
                .padding(8.dp)
        )
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(40.dp))
    }
}
