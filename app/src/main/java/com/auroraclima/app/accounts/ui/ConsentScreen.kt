package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.SteelDeep

// Los textos del mockup P04 se reformularon sin los términos que
// CamouflageResourcesTest veda (auditoría D3 H-1/H-2): tanto resources.arsc
// como los literales del DEX llegan legibles al APK, así que la única
// protección real es la redacción. Reformulados, viven en strings.xml con
// claves neutras, dentro del alcance del guardián.
private val INFO_SECTIONS = listOf(
    R.string.account_intro_keep_title to R.string.account_intro_keep_body,
    R.string.account_intro_avoid_title to R.string.account_intro_avoid_body,
    R.string.account_intro_rights_title to R.string.account_intro_rights_body
)

/**
 * Consentimiento informado (RF-17), mockup alta-fidelidad-08 (P04).
 * «No aceptar» converge en la salida rápida: regreso inmediato al clima sin
 * dejar rastro y sin haber recolectado nada (RF-18).
 */
@Composable
fun ConsentRoute(onAccepted: () -> Unit) {
    val viewModel: ConsentViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    ConsentScreen(
        state = state,
        onAcceptanceChange = viewModel::onAcceptanceChange,
        onAccept = onAccepted,
        onReject = sessionManager::quickExit
    )
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
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.account_intro_tag))
        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.account_intro_title))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.account_intro_body),
            fontFamily = PlexSans,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(20.dp))
        ConsentInfoCard()
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = state.isAccepted,
                onCheckedChange = onAcceptanceChange,
                colors = CheckboxDefaults.colors(checkedColor = Night)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.account_intro_check),
                fontFamily = PlexSans,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Night,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAccept,
            // RF-17 criterio 1: imposible continuar sin aceptación explícita.
            enabled = state.isAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Night,
                contentColor = CloudWhite,
                disabledContainerColor = Sky,
                disabledContentColor = Night.copy(alpha = 0.45f)
            )
        ) {
            Text(
                text = stringResource(R.string.account_intro_accept),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onReject,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.account_intro_reject),
                fontFamily = PlexSans,
                fontSize = 15.sp,
                color = SteelDeep
            )
        }
        // Aire final para no chocar con el botón nube de salida rápida.
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Tarjeta con el texto del acuerdo de datos (claves account_intro_*). Interna al
 * feature: la pinta el primer uso (RF-17 criterio 1) y la relectura desde la
 * sección de privacidad (RF-17 criterio 2, P-16).
 */
@Composable
internal fun ConsentInfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CloudWhite
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            INFO_SECTIONS.forEachIndexed { index, (titleRes, bodyRes) ->
                if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                val title = stringResource(titleRes)
                val body = stringResource(bodyRes)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(title)
                        }
                        append(" ")
                        append(body)
                    },
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Night
                )
            }
        }
    }
}
