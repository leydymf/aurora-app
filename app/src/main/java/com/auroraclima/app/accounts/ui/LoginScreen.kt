package com.auroraclima.app.accounts.ui

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.auroraclima.app.core.ui.v2.V2PlainField
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Inicio de sesión (RF-15), mockup v2-09 (P08, con P08·b heredado). Error
 * genérico que no señala campo (criterio 2) y «pausa temporal» con cuenta
 * regresiva tras 5 intentos (criterio 3, decisión F3/F4: lenguaje de cuidado,
 * sin rojos de alarma). Los campos reportan cada tecla como interacción (P-15).
 */
@Composable
fun LoginRoute(onAccessGranted: () -> Unit) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.openEvents.collect { onAccessGranted() }
    }
    AuroraV2Theme(V2Voice.Interior) {
        LoginScreen(
            state = state,
            onEmailChange = {
                sessionManager.registerInteraction()
                viewModel.onEmailChange(it)
            },
            onPasswordChange = {
                sessionManager.registerInteraction()
                viewModel.onPasswordChange(it)
            },
            onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
            onSubmit = {
                // También el envío con la tecla «Done» del IME cuenta como interacción.
                sessionManager.registerInteraction()
                viewModel.onSubmit()
            }
        )
    }
}

@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
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
        // Mientras se decide si ya hay sesión no se pinta el formulario:
        // con sesión vigente esta pantalla ni se ve (RF-12 criterio 1).
        if (state.isCheckingSession) return

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.account_login_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.account_login_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        SoftCard {
            V2FieldLabel(stringResource(R.string.account_register_email_label))
            V2PlainField(
                value = state.email,
                onValueChange = onEmailChange,
                isError = state.errorRes != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            V2FieldLabel(stringResource(R.string.account_register_password_label))
            V2SecretField(
                value = state.password,
                onValueChange = onPasswordChange,
                isVisible = state.isPasswordVisible,
                onToggleVisibility = onTogglePasswordVisibility,
                isError = state.errorRes != null,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                toggleDescription = stringResource(R.string.account_password_toggle)
            )
            // Error genérico bajo los campos: nunca dice cuál falló (criterio 2).
            V2FieldMessage(messageRes = state.errorRes, fallbackRes = null)
        }

        state.lockCountdownText?.let { countdown ->
            Spacer(modifier = Modifier.height(20.dp))
            LockCard(countdown)
        }
        Spacer(modifier = Modifier.height(24.dp))

        V2PrimaryButton(
            text = stringResource(R.string.account_login_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            isLoading = state.isLoading
        )
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Tarjeta «Pausa temporal» del mockup P08·b: cuidado, no castigo (F3/F4).
 * Arena porque la pausa es TIEMPO (regla 1) y el reloj de la librería la
 * acompaña; la cuenta regresiva va siempre con su texto (regla 3).
 */
@Composable
private fun LockCard(countdown: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(V2Corner.control)
            .background(V2Palette.accentJustice.tint)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(V2Icons.waiting),
                contentDescription = null,
                tint = V2Palette.accentJustice.stroke,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.account_login_lock_title),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.accentJustice.stroke
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.account_login_lock_body),
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentJustice.stroke
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.account_login_lock_countdown, countdown),
            style = InteriorTypeVoice.label,
            color = V2Palette.accentJustice.stroke
        )
    }
}
