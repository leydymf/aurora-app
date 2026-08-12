package com.auroraclima.app.core.ui.v2

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Kit de formulario del sistema V2 (mockups v2-06/07/09): campo relleno en el
 * lila del separador sobre tarjeta o fondo, esquina de control, error en el rosa
 * de avisos (regla 1: el rosa siempre es aviso). Los campos del mockup llevan un
 * icono decorativo inicial (sobre, candado); se omite a propósito: el candado lo
 * veda la regla 4 y la librería cerrada no tiene sobre — el campo informa por su
 * etiqueta, no por el dibujo (regla 3).
 *
 * Mismo criterio de ubicación que el kit V1 (FormElements): vive en core porque
 * accounts y access no se importan entre sí (§2 de ARQUITECTURA.md).
 */
@Composable
fun V2FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = InteriorTypeVoice.label,
        color = V2Palette.inkPrimary,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/** Campo de una línea sin enmascarar (correo, textos cortos). */
@Composable
fun V2PlainField(
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
        textStyle = InteriorTypeVoice.body.copy(color = V2Palette.inkPrimary),
        singleLine = true,
        isError = isError,
        shape = V2Corner.control,
        colors = v2FieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

/** Campo enmascarado con ojo de visibilidad (contraseñas y clave personal). */
@Composable
fun V2SecretField(
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
        textStyle = InteriorTypeVoice.body.copy(color = V2Palette.inkPrimary),
        singleLine = true,
        isError = isError,
        shape = V2Corner.control,
        colors = v2FieldColors(),
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
                    tint = V2Palette.inkSecondary
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

/**
 * Mensaje bajo el campo: error en el rosa profundo de avisos (6.6:1 sobre el
 * tinte, 7.3:1 sobre el fondo), o ayuda fija en tinta secundaria si no hay error.
 */
@Composable
fun V2FieldMessage(@StringRes messageRes: Int?, @StringRes fallbackRes: Int?, modifier: Modifier = Modifier) {
    val (textRes, color) = when {
        messageRes != null -> messageRes to V2Palette.accentNotice.stroke
        fallbackRes != null -> fallbackRes to V2Palette.inkSecondary
        else -> return
    }
    Text(
        text = stringResource(textRes),
        style = InteriorTypeVoice.caption,
        color = color,
        modifier = modifier.padding(top = 6.dp)
    )
}

/**
 * Encabezado «Paso N de M» del primer uso (P05–P07): segmentos + texto SIEMPRE
 * juntos — el relleno lavanda es refuerzo, nunca el único indicador (regla 3,
 * README de mockups-v2 §3.6).
 */
@Composable
fun V2StepHeader(step: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SegmentedProgressBar(
            totalSegments = totalSteps,
            completedSegments = step,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.common_step_label, step, totalSteps),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
    }
}

@Composable
private fun v2FieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = V2Palette.divider,
    unfocusedContainerColor = V2Palette.divider,
    errorContainerColor = V2Palette.cardSurface,
    focusedBorderColor = V2Palette.inkSecondary,
    unfocusedBorderColor = V2Palette.divider,
    errorBorderColor = V2Palette.accentNotice.stroke,
    cursorColor = V2Palette.inkPrimary,
    errorCursorColor = V2Palette.accentNotice.stroke
)

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun V2FieldsPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        Column(modifier = Modifier.padding(16.dp)) {
            V2StepHeader(step = 1, totalSteps = 3)
            Spacer(modifier = Modifier.padding(top = 16.dp))
            V2FieldLabel("Correo")
            V2PlainField(value = "demo@correo.co", onValueChange = {})
            V2FieldMessage(messageRes = null, fallbackRes = null)
        }
    }
}
