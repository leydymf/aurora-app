package com.auroraclima.app.content.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.Agency
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecondaryButton
import com.auroraclima.app.core.ui.v2.routeVisual

/**
 * Detalle de entidad (RF-22, mockup P13 / v2-15). El botón de llamada abre el
 * marcador del teléfono con ACTION_DIAL: no exige permiso alguno (RNF-04), no
 * marca por sí solo y la app no conserva rastro de la acción (decisión F5).
 */
@Composable
fun AgencyDetailRoute(onBack: () -> Unit) {
    val viewModel: AgencyDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    AgencyDetailScreen(
        state = state,
        onBack = onBack,
        onCall = { phone -> dialNumber(context, phone) },
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun AgencyDetailScreen(
    state: AgencyDetailUiState,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        V2BackHeader(
            tag = state.routeTitle,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null || state.agency == null ->
                V2ErrorState(messageRes = R.string.content_error_generic, onRetry = onRetry)
            else -> AgencyDetailContent(routeId = state.routeId, agency = state.agency, onCall = onCall)
        }
    }
}

@Composable
private fun AgencyDetailContent(routeId: String, agency: Agency, onCall: (String) -> Unit) {
    val sectorVisual = routeVisual(routeId)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(iconRes = sectorVisual.iconRes, accent = sectorVisual.accent)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = agency.name,
                    style = InteriorTypeVoice.screenTitle,
                    color = V2Palette.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Las tres secciones en una tarjeta (v2-15): qué hace (información,
            // neutro), cuándo acudir (reloj, arena de tiempo) y dónde (pin,
            // verde de cuidado) — significados fijos de la regla 1.
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AgencySection(
                        iconRes = V2Icons.info,
                        accent = V2Palette.accentGroup,
                        label = stringResource(R.string.content_agency_role_label),
                        text = agency.role
                    )
                    AgencySection(
                        iconRes = V2Icons.waiting,
                        accent = V2Palette.accentJustice,
                        label = stringResource(R.string.content_agency_when_label),
                        text = agency.whenToUse
                    )
                    if (agency.address != null || agency.hours != null) {
                        AgencySection(
                            iconRes = V2Icons.place,
                            accent = V2Palette.accentCare,
                            label = stringResource(R.string.content_agency_where_label),
                            text = listOfNotNull(
                                agency.address,
                                agency.hours?.let { stringResource(R.string.content_agency_hours, it) }
                            ).joinToString(separator = "\n")
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (agency.phones.isNotEmpty()) {
                // Aviso rosa (v2-15): la llamada sale del camuflaje y la nota lo
                // advierte antes del botón (D4 M-2).
                CallNoticeCard(text = stringResource(R.string.content_agency_call_note))
                // Números adicionales como acción secundaria (regla 2: un solo
                // botón lleno por pantalla — el primero, fijo abajo).
                agency.phones.drop(1).forEach { phone ->
                    Spacer(modifier = Modifier.height(10.dp))
                    V2SecondaryButton(
                        text = stringResource(R.string.content_agency_call, phone),
                        onClick = { onCall(phone) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        agency.phones.firstOrNull()?.let { phone ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
            ) {
                V2PrimaryButton(
                    text = stringResource(R.string.content_agency_call, phone),
                    onClick = { onCall(phone) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(CLOUD_CLEARANCE))
            }
        }
    }
}

/** Sección del detalle (v2-15): insignia + etiqueta + texto. */
@Composable
private fun AgencySection(@DrawableRes iconRes: Int, accent: AccentPair, label: String, text: String) {
    Row {
        AccentBadge(iconRes = iconRes, accent = accent, size = 40.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = InteriorTypeVoice.bodyStrong, color = V2Palette.inkPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = text, style = InteriorTypeVoice.body, color = V2Palette.inkSecondary)
        }
    }
}

/** Aviso rosa de la llamada (v2-15): escudo y texto en el trazo del acento de aviso. */
@Composable
private fun CallNoticeCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(V2Corner.card)
            .background(V2Palette.accentNotice.tint)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(V2Icons.protection),
            contentDescription = null,
            tint = V2Palette.accentNotice.stroke,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentNotice.stroke,
            modifier = Modifier.weight(1f)
        )
    }
}
