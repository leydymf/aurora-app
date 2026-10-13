package com.auroraclima.app.accounts.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2Voice

// Los textos del mockup P04 se reformularon sin los términos que
// CamouflageResourcesTest veda (auditoría D3 H-1/H-2): tanto resources.arsc
// como los literales del DEX llegan legibles al APK, así que la única
// protección real es la redacción. Reformulados, viven en strings.xml con
// claves neutras, dentro del alcance del guardián.
//
// Iconos y acentos por punto (v2-05) desde la librería cerrada, con los
// significados fijos de la regla 1 por delante de los matices del mockup:
// llave = lo guardado protegido; círculo con barra = límite («qué no
// hacemos»); escudo azul = protección de derechos. Sin candados (regla 4).
private data class ConsentPoint(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
    val accent: AccentPair
)

private val INFO_SECTIONS = listOf(
    ConsentPoint(
        titleRes = R.string.account_intro_keep_title,
        bodyRes = R.string.account_intro_keep_body,
        iconRes = V2Icons.code,
        accent = V2Palette.accentRecognize
    ),
    ConsentPoint(
        titleRes = R.string.account_intro_avoid_title,
        bodyRes = R.string.account_intro_avoid_body,
        iconRes = V2Icons.slashCircle,
        accent = V2Palette.accentGroup
    ),
    ConsentPoint(
        titleRes = R.string.account_intro_rights_title,
        bodyRes = R.string.account_intro_rights_body,
        iconRes = V2Icons.protection,
        accent = V2Palette.accentProtection
    )
)

/**
 * Consentimiento informado (RF-17), mockup v2-05 (P04).
 * «No aceptar» converge en la salida rápida: regreso inmediato al clima sin
 * dejar rastro y sin haber recolectado nada (RF-18).
 */
@Composable
fun ConsentRoute(onAccepted: () -> Unit) {
    val viewModel: ConsentViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    AuroraV2Theme(V2Voice.Interior) {
        ConsentScreen(
            state = state,
            onAcceptanceChange = viewModel::onAcceptanceChange,
            onAccept = onAccepted,
            onReject = sessionManager::closeNow
        )
    }
}

@Composable
internal fun ConsentScreen(
    state: ConsentUiState,
    onAcceptanceChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
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
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.account_intro_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.account_intro_body),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        ConsentInfoCard()
        Spacer(modifier = Modifier.height(16.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isAccepted,
                    onCheckedChange = onAcceptanceChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = V2Palette.inkPrimary,
                        checkmarkColor = V2Palette.cardSurface,
                        uncheckedColor = V2Palette.inkSecondary
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.account_intro_check),
                    style = InteriorTypeVoice.body,
                    color = V2Palette.inkPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        V2PrimaryButton(
            text = stringResource(R.string.account_intro_accept),
            onClick = onAccept,
            // RF-17 criterio 1: imposible continuar sin aceptación explícita.
            enabled = state.isAccepted
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onReject,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.account_intro_reject),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.inkSecondary
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Tarjeta con el texto del acuerdo de datos (claves account_intro_*), un punto
 * por fila con su icono de la librería cerrada (v2-05). Interna al feature: la
 * pinta el primer uso (RF-17 criterio 1) y la relectura desde la sección de
 * privacidad (RF-17 criterio 2, P-16), que hereda este estilo por extensión (D6).
 */
@Composable
internal fun ConsentInfoCard(modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        INFO_SECTIONS.forEachIndexed { index, point ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            IconListRow(
                iconRes = point.iconRes,
                accent = point.accent,
                title = stringResource(point.titleRes),
                supportingText = stringResource(point.bodyRes)
            )
        }
    }
}
