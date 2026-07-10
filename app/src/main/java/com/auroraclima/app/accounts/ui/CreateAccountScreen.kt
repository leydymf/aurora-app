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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.PlainField
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SecretField
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum

/**
 * Creación de cuenta (RF-07), mockup alta-fidelidad-09 (P05). Paso 1 de 3 del
 * primer uso. Los campos reportan cada tecla como interacción (P-15): el teclado
 * es una ventana propia sin eventos de puntero sobre la app y el temporizador de
 * inactividad (RNF-03) expiraría mientras la usuaria escribe.
 */
@Composable
fun CreateAccountRoute(onAccountCreated: () -> Unit) {
    val viewModel: CreateAccountViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.continueEvents.collect { onAccountCreated() }
    }
    CreateAccountScreen(
        state = state,
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

@Composable
internal fun CreateAccountScreen(
    state: CreateAccountUiState,
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
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.account_register_tag))
        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.account_register_title))
        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.account_register_email_label))
        PlainField(
            value = state.email,
            onValueChange = onEmailChange,
            isError = state.emailErrorRes != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        FieldMessage(messageRes = state.emailErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.account_register_password_label))
        SecretField(
            value = state.password,
            onValueChange = onPasswordChange,
            isVisible = state.isPasswordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
            isError = state.passwordErrorRes != null,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(),
            toggleDescription = stringResource(R.string.account_password_toggle)
        )
        FieldMessage(
            messageRes = state.passwordErrorRes,
            fallbackRes = R.string.account_register_password_hint
        )
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.account_register_confirm_label))
        SecretField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            isVisible = state.isConfirmVisible,
            onToggleVisibility = onToggleConfirmVisibility,
            isError = state.confirmErrorRes != null,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            toggleDescription = stringResource(R.string.account_password_toggle)
        )
        FieldMessage(messageRes = state.confirmErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(R.string.account_register_submit),
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
