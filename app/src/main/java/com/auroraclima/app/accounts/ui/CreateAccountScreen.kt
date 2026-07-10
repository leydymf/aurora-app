package com.auroraclima.app.accounts.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

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
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = fieldTextStyle(),
            singleLine = true,
            isError = state.emailErrorRes != null,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors(),
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
            keyboardActions = KeyboardActions()
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
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        FieldMessage(messageRes = state.confirmErrorRes, fallbackRes = null)
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Night,
                contentColor = CloudWhite,
                disabledContainerColor = Night.copy(alpha = 0.55f),
                disabledContentColor = CloudWhite
            )
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = CloudWhite,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.account_register_submit),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
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

/** Campo enmascarado con ojo de visibilidad (contraseña y su confirmación). */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = fieldTextStyle(),
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(),
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    painter = painterResource(
                        if (isVisible) R.drawable.ic_field_hide else R.drawable.ic_field_show
                    ),
                    contentDescription = stringResource(R.string.account_password_toggle),
                    tint = Steel
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = Night,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** Mensaje bajo el campo: error en ciruela, o ayuda fija en acero si no hay error. */
@Composable
private fun FieldMessage(@StringRes messageRes: Int?, @StringRes fallbackRes: Int?) {
    val (textRes, color) = when {
        messageRes != null -> messageRes to Plum
        fallbackRes != null -> fallbackRes to SteelDeep
        else -> return
    }
    Text(
        text = stringResource(textRes),
        fontFamily = PlexSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = color,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun fieldTextStyle() = TextStyle(
    fontFamily = PlexSans,
    fontSize = 16.sp,
    color = Night
)

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CloudWhite,
    unfocusedContainerColor = CloudWhite,
    errorContainerColor = CloudWhite,
    focusedBorderColor = SteelDeep,
    unfocusedBorderColor = Sky,
    errorBorderColor = Plum,
    cursorColor = Night,
    errorCursorColor = Plum
)
