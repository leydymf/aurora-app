package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDialog
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.DestructiveButton
import com.auroraclima.app.core.ui.components.FieldLabel
import com.auroraclima.app.core.ui.components.FieldMessage
import com.auroraclima.app.core.ui.components.SecretField
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Eliminación total de la cuenta (RF-20), fila «CUENTA» de Configuración (P21).
 * La pantalla explica alcance e irreversibilidad; la confirmación son dos pasos
 * en [SecureDialog] (criterio 1): entender qué se borra y teclear la contraseña
 * (que alimenta la re-autenticación del caso de uso). El campo reporta cada
 * tecla como interacción (P-15). Al terminar no hay mensaje de éxito: el
 * regreso al clima es la única señal, como toda salida (camuflaje F8).
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
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BackHeader(tag = stringResource(R.string.account_delete_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.account_delete_title))
        Spacer(modifier = Modifier.height(16.dp))
        BodyText(stringResource(R.string.account_delete_body))
        Spacer(modifier = Modifier.height(12.dp))
        BodyText(stringResource(R.string.account_delete_requirements))
        Spacer(modifier = Modifier.height(28.dp))
        DestructiveButton(
            text = stringResource(R.string.account_delete_action),
            onClick = onStartConfirmation,
            enabled = true,
            isLoading = false,
            modifier = Modifier.fillMaxWidth()
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
        Surface(shape = RoundedCornerShape(20.dp), color = CloudWhite) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                DialogTitle(stringResource(R.string.account_delete_confirm_title))
                Spacer(modifier = Modifier.height(10.dp))
                BodyText(stringResource(R.string.account_delete_confirm_body))
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogCancelButton(onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    DestructiveButton(
                        text = stringResource(R.string.account_delete_confirm_continue),
                        onClick = onContinue,
                        enabled = true,
                        isLoading = false
                    )
                }
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
        Surface(shape = RoundedCornerShape(20.dp), color = CloudWhite) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                DialogTitle(stringResource(R.string.account_delete_password_title))
                Spacer(modifier = Modifier.height(10.dp))
                BodyText(stringResource(R.string.account_delete_password_body))
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
                FieldMessage(messageRes = state.errorRes, fallbackRes = null)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogCancelButton(onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    DestructiveButton(
                        text = stringResource(R.string.account_delete_submit),
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        isLoading = state.isWorking
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = Plum
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        fontFamily = PlexSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = SteelDeep
    )
}

@Composable
private fun DialogCancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = stringResource(R.string.account_delete_confirm_cancel),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            color = SteelDeep
        )
    }
}
