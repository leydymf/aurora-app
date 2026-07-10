package com.auroraclima.app.content.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.content.domain.model.Agency
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Detalle de entidad (RF-22, mockup P13 / alta-fidelidad-21). El botón de llamada
 * abre el marcador del teléfono con ACTION_DIAL: no exige permiso alguno (RNF-04),
 * no marca por sí solo y la app no conserva rastro de la acción (decisión F5).
 */
@Composable
fun AgencyDetailRoute(onBack: () -> Unit) {
    val viewModel: AgencyDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    AgencyDetailScreen(
        state = state,
        onBack = onBack,
        onCall = { phone ->
            // Sin marcador instalado (p. ej. tablet WiFi) la acción se descarta en
            // silencio: un crash sería una anomalía visible y quedaría en logcat
            // (auditoría D4 S2 B-1). Nada se registra.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
            }
        },
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
            .background(Mist)
            .systemBarsPadding()
    ) {
        ContentBackHeader(
            tag = stringResource(R.string.content_agency_tag, state.routeTitle.uppercase()),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when {
            state.isLoading -> ContentLoadingState()
            state.errorRes != null || state.agency == null -> ContentErrorState(onRetry = onRetry)
            else -> AgencyDetailContent(agency = state.agency, onCall = onCall)
        }
    }
}

@Composable
private fun AgencyDetailContent(agency: Agency, onCall: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SectionTitle(agency.name)
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.content_agency_role_label))
        Spacer(modifier = Modifier.height(8.dp))
        BodyParagraph(agency.role)
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.content_agency_when_label))
        Spacer(modifier = Modifier.height(8.dp))
        BodyParagraph(agency.whenToUse)
        if (agency.address != null || agency.hours != null) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionTag(stringResource(R.string.content_agency_where_label))
            Spacer(modifier = Modifier.height(10.dp))
            WhereCard(address = agency.address, hours = agency.hours)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            agency.phones.forEach { phone -> CallButton(phone = phone, onCall = onCall) }
        }
        if (agency.phones.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.content_agency_call_note),
                fontFamily = PlexSans,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = SteelDeep,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(CONTENT_BOTTOM_CLEARANCE))
    }
}

@Composable
private fun BodyParagraph(text: String) {
    Text(
        text = text,
        fontFamily = PlexSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = Night
    )
}

@Composable
private fun WhereCard(address: String?, hours: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (address != null) {
                Text(
                    text = address,
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Night
                )
            }
            if (address != null && hours != null) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (hours != null) {
                Text(
                    text = stringResource(R.string.content_agency_hours, hours),
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SteelDeep
                )
            }
        }
    }
}

@Composable
private fun CallButton(phone: String, onCall: (String) -> Unit) {
    Button(
        onClick = { onCall(phone) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Night,
            contentColor = CloudWhite
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.content_agency_call, phone),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}
