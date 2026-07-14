package com.auroraclima.app.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Kit de formulario del espacio interno (mockups P04–P08): mismo campo, etiqueta,
 * mensaje y botón en creación de cuenta, clave personal e inicio de sesión.
 * Vive en core porque las features no se importan entre sí (§2 de ARQUITECTURA.md;
 * mismo criterio que SectionTag/SectionTitle).
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = Night,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/** Campo de una línea sin enmascarar (correo, textos cortos). */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions()
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
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

/** Campo enmascarado con ojo de visibilidad (contraseñas y clave personal). */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    toggleDescription: String,
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
                    contentDescription = toggleDescription,
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

/** Campo de párrafo multilínea (relatos y notas largas, P19). */
@Composable
fun ParagraphField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        textStyle = fieldTextStyle(),
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(),
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = PlexSans,
                fontSize = 16.sp,
                color = Steel
            )
        }
    )
}

/** Mensaje bajo el campo: error en ciruela, o ayuda fija en acero si no hay error. */
@Composable
fun FieldMessage(@StringRes messageRes: Int?, @StringRes fallbackRes: Int?) {
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

/** Botón principal en Noche con indicador de carga (P05–P08). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
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
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = CloudWhite,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
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
