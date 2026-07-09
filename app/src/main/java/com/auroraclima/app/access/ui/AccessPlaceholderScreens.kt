package com.auroraclima.app.access.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.auroraclima.app.core.security.SecureScreen
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night

/**
 * Destino del código de activación (RF-05): aquí empezará el consentimiento y la
 * creación de cuenta. TODO(RF-17, RF-07): pantalla real en el día 3 (mockup
 * alta-fidelidad-08).
 */
@Composable
fun ActivationPlaceholderScreen(modifier: Modifier = Modifier) {
    AccessPlaceholder(titleRes = R.string.access_intro_title, modifier = modifier)
}

/**
 * Destino del código secreto de la usuaria (RF-12): inicio de sesión o pantalla
 * principal según haya sesión. TODO(RF-15): pantalla real en el día 3 (mockups
 * alta-fidelidad-14 y 16).
 */
@Composable
fun AccessPlaceholderScreen(modifier: Modifier = Modifier) {
    AccessPlaceholder(titleRes = R.string.access_entry_title, modifier = modifier)
}

@Composable
private fun AccessPlaceholder(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    SecureScreen {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Night)
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(titleRes),
                color = Mist,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.access_placeholder_body),
                color = Mist.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
