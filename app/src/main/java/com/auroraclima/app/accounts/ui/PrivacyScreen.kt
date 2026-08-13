package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Palette

/**
 * Relectura del acuerdo de datos desde configuración (RF-17 criterio 2, P-16):
 * el mismo texto del primer uso (claves account_intro_*, ConsentInfoCard), en
 * solo lectura — la aceptación no se persiste ni se re-pregunta (decisión
 * 2026-07-09: solo habilita continuar en el primer uso, minimización RNF-04).
 * Sin ViewModel: no hay estado (mismo criterio que PanelScreen).
 *
 * V2 (cierre): re-estilo por extensión del sistema (decisión D6) — reutiliza
 * tal cual la ConsentInfoCard ya migrada del primer uso (P04).
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        V2BackHeader(tag = stringResource(R.string.account_privacy_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.account_privacy_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.account_intro_body),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        ConsentInfoCard()
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(64.dp))
    }
}
