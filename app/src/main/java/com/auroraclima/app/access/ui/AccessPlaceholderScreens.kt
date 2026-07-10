package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night

/**
 * Destino tras crear la clave (paso 3 de 3): aquí vivirá el tutorial con la
 * práctica de ingreso y salida. TODO(RF-10): pantalla real (mockups
 * alta-fidelidad-11/12, P07); por ahora solo deja continuar al hogar interno.
 *
 * Las pantallas internas NO aplican la bandera de pantalla segura por su cuenta:
 * la garantiza el grafo seguro de `core/security` donde se registran sus rutas (RNF-02).
 */
@Composable
fun GuidePlaceholderScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.access_guide_title),
            color = Night,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.access_guide_body),
            color = Night.copy(alpha = 0.7f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        PrimaryButton(
            text = stringResource(R.string.access_guide_continue),
            onClick = onContinue,
            enabled = true,
            isLoading = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
