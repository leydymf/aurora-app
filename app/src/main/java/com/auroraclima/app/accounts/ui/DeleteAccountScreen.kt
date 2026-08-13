package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDialog
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2DestructiveButton
import com.auroraclima.app.core.ui.v2.V2FieldLabel
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecretField

/**
 * Eliminación total de la cuenta (RF-20), fila «CUENTA» de Configuración (P21).
 * La pantalla explica alcance e irreversibilidad; la confirmación son dos pasos
 * en [SecureDialog] (criterio 1): entender qué se borra y teclear la contraseña
 * (que alimenta la re-autenticación del caso de uso). El campo reporta cada
 * tecla como interacción (P-15). Al terminar no hay mensaje de éxito: el
 * regreso al clima es la única señal, como toda salida (camuflaje F8).
 *
 * V2 (cierre): re-estilo por extensión del sistema (decisión D6, sin mockup V2
 * propio). Los diálogos siguen el patrón de la hoja de P20·b (v2-22): quedarse
 * («Ahora no») es la ÚNICA acción llena y el paso destructivo va como texto en
 * el rosa profundo — conservar es siempre el camino principal.
 */
@Composable
fun DeleteAccountRoute(onBack: () -> Unit, onDeleted: () -> Unit) {
    val viewModel: DeleteAccountViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(viewModel) {
        viewModel.doneEvents.collect { onDeleted() }
    }
    DeleteAccountScreen(
        state = state,
        onBack = onBack,
        onStartConfirmation = viewModel::onStartConfirmation,
        onScopeConfirmed = viewModel::onScopeConfirmed,
        onDismissDialogs = viewModel::onDismissDialogs,
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
internal fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onStartConfirmation: () -> Unit,
    onScopeConfirmed: () -> Unit,
    onDismissDialogs: () -> Unit,
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
        V2BackHeader(tag = stringResource(R.string.account_delete_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_delete_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_delete_body),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.account_delete_requirements),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(28.dp))
        V2DestructiveButton(
            text = stringResource(R.string.account_delete_action),
            onClick = onStartConfirmation
        )
        Spacer(modifier = Modifier.height(64.dp))
    }
    when (state.step) {
        DeleteAccountStep.Overview -> Unit
        DeleteAccountStep.ConfirmScope -> ScopeConfirmDialog(
            onContinue = onScopeConfirmed,
            onDismiss = onDismissDialogs
        )
        DeleteAccountStep.ConfirmPassword -> PasswordConfirmDialog(
            state = state,
            onPasswordChange = onPasswordChange,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onSubmit = onSubmit,
            onDismiss = onDismissDialogs
        )
    }
}

/** Paso 1 (RF-20 criterio 1): qué se elimina y que no hay vuelta atrás. */
@Composable
private fun ScopeConfirmDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    SecureDialog(onDismissRequest = onDismiss) {
        SoftCard {
            AccentBadge(
                iconRes = V2Icons.delete,
                accent = V2Palette.accentNotice,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.account_delete_confirm_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.account_delete_confirm_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(18.dp))
            V2PrimaryButton(
                text = stringResource(R.string.account_delete_confirm_cancel),
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onContinue,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.account_delete_confirm_continue),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.accentNotice.stroke
                )
            }
        }
    }
}

/** Paso 2: la contraseña confirma y re-autentica; los errores se muestran aquí mismo. */
@Composable
private fun PasswordConfirmDialog(
    state: DeleteAccountUiState,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    SecureDialog(onDismissRequest = onDismiss) {
        SoftCard {
            Text(
                text = stringResource(R.string.account_delete_password_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.account_delete_password_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary
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
            V2FieldMessage(messageRes = state.errorRes, fallbackRes = null)
            Spacer(modifier = Modifier.height(18.dp))
            V2PrimaryButton(
                text = stringResource(R.string.account_delete_confirm_cancel),
                onClick = onDismiss,
                enabled = !state.isWorking
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (state.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 10.dp)
                        .size(22.dp),
                    color = V2Palette.accentNotice.stroke,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.account_delete_submit),
                        style = InteriorTypeVoice.bodyStrong,
                        color = if (state.canSubmit) {
                            V2Palette.accentNotice.stroke
                        } else {
                            V2Palette.inkSecondary
                        }
                    )
                }
            }
        }
    }
}
