package com.auroraclima.app.accounts.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Inicio de sesión (RF-15), mockups alta-fidelidad-14/15 (P08 y P08·b). Error
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
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BrandHeader()
        // Mientras se decide si ya hay sesión no se pinta el formulario:
        // con sesión vigente esta pantalla ni se ve (RF-12 criterio 1).
        if (state.isCheckingSession) return

        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.account_login_tag))
        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.account_login_title))
        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(stringResource(R.string.account_register_email_label))
        PlainField(
            value = state.email,
            onValueChange = onEmailChange,
            isError = state.errorRes != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.account_register_password_label))
        SecretField(
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
        FieldMessage(messageRes = state.errorRes, fallbackRes = null)

        state.lockCountdownText?.let { countdown ->
            Spacer(modifier = Modifier.height(20.dp))
            LockCard(countdown)
        }
        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(R.string.account_login_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            isLoading = state.isLoading
        )
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/** Tarjeta «Pausa temporal» del mockup P08·b: cuidado, no castigo (F3/F4). */
@Composable
private fun LockCard(countdown: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudWhite, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.account_login_lock_title),
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Night
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.account_login_lock_body),
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Night.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.account_login_lock_countdown, countdown),
            fontFamily = PlexMono,
            fontSize = 12.sp,
            letterSpacing = 0.16.em,
            color = SteelDeep
        )
    }
}
