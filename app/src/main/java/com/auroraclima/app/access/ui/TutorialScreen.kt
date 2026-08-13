package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2StepHeader
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Tutorial del espacio interno (RF-10; mockup v2-08, P07). Paso 3 de 3.
 * La vista V2 presenta los TRES momentos juntos en tarjetas (antes, pager de
 * tres páginas): la máquina de pasos sigue siendo del ViewModel — «Practicar»
 * invoca su salto directo a la práctica (`onStartPractice`, hallazgo M-1 de la
 * revisión S2; cambio de VM justificado en el plan de sesión aprobado).
 *
 * La práctica de salida es REAL (decisión de la usuaria sobre P07·b y la nota
 * F3·F4 de A2-6): la nube de la esquina — pintada por el grafo seguro — sale de
 * verdad a la fachada, y el reingreso con la clave ejercita RF-12 tal como será
 * siempre. En modo repaso (RF-11) la única diferencia es el rótulo y que
 * «Atrás» regresa a donde estaba la usuaria.
 */
@Composable
fun TutorialRoute(isReview: Boolean, onClose: () -> Unit) {
    val viewModel: TutorialViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    AuroraV2Theme(V2Voice.Interior) {
        TutorialScreen(
            state = state,
            isReview = isReview,
            onStartPractice = viewModel::onStartPractice,
            onBack = { if (state.isFirstStep) onClose() else viewModel.onBack() },
            onPracticeInputChange = {
                // El teclado es una ventana propia: cada tecla debe contar como
                // interacción o la inactividad expulsaría a mitad de práctica (P-15).
                sessionManager.registerInteraction()
                viewModel.onPracticeInputChange(it)
            }
        )
    }
}

@Composable
internal fun TutorialScreen(
    state: TutorialUiState,
    isReview: Boolean,
    onStartPractice: () -> Unit,
    onBack: () -> Unit,
    onPracticeInputChange: (String) -> Unit,
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
        Spacer(modifier = Modifier.height(16.dp))
        if (state.step == TutorialStep.PRACTICE) {
            PracticeContent(
                input = state.practiceInput,
                status = state.practiceStatus,
                onInputChange = onPracticeInputChange
            )
        } else {
            OverviewContent(
                isReview = isReview,
                showBack = state.showsBack(isReview),
                onStartPractice = onStartPractice,
                onBack = onBack
            )
        }
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(64.dp))
    }
}

/** Los tres momentos del mockup v2-08 en una sola pantalla, con la librería cerrada. */
private data class TutorialMoment(
    @DrawableRes val iconRes: Int,
    val accent: AccentPair,
    val titleRes: Int,
    val bodyRes: Int
)

private val MOMENTS = listOf(
    // La llave abre (regla 4: llave, nunca candado); la nube violeta es exclusiva
    // de la salida; el escudo azul cuenta lo que un tercero NO llega a ver.
    TutorialMoment(
        iconRes = V2Icons.code,
        accent = V2Palette.accentRecognize,
        titleRes = R.string.access_guide_enter_title,
        bodyRes = R.string.access_guide_enter_body
    ),
    TutorialMoment(
        iconRes = V2Icons.cloud,
        accent = V2Palette.accentCloud,
        titleRes = R.string.access_guide_exit_title,
        bodyRes = R.string.access_guide_exit_body
    ),
    TutorialMoment(
        iconRes = V2Icons.protection,
        accent = V2Palette.accentProtection,
        titleRes = R.string.access_guide_close_title,
        bodyRes = R.string.access_guide_close_body
    )
)

@Composable
private fun OverviewContent(isReview: Boolean, showBack: Boolean, onStartPractice: () -> Unit, onBack: () -> Unit) {
    if (isReview) {
        Text(
            text = stringResource(R.string.access_guide_tag_review),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
    } else {
        V2StepHeader(step = 3, totalSteps = 3)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.access_guide_overview_title),
        style = InteriorTypeVoice.screenTitle,
        color = V2Palette.inkPrimary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.access_guide_overview_subtitle),
        style = InteriorTypeVoice.body,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(20.dp))
    MOMENTS.forEach { moment ->
        SoftCard(modifier = Modifier.padding(bottom = 12.dp)) {
            IconListRow(
                iconRes = moment.iconRes,
                accent = moment.accent,
                title = stringResource(moment.titleRes),
                supportingText = stringResource(moment.bodyRes)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    PracticeTeaserCard()
    Spacer(modifier = Modifier.height(24.dp))
    V2PrimaryButton(
        text = stringResource(R.string.access_guide_practice_cta),
        onClick = onStartPractice
    )
    if (showBack) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.access_guide_back),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkSecondary
                )
            }
        }
    }
}

/** Tarjeta rosa «Practiquemos ahora» del mockup (el rosa siempre es aviso/cuidado, regla 1). */
@Composable
private fun PracticeTeaserCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(V2Corner.card)
            .background(V2Palette.accentNotice.tint)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(V2Icons.waiting),
                contentDescription = null,
                tint = V2Palette.accentNotice.stroke,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_card_title),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.accentNotice.stroke
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.access_guide_practice_teaser),
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentNotice.stroke
        )
    }
}

@Composable
private fun PracticeContent(input: String, status: PracticeStatus, onInputChange: (String) -> Unit) {
    Text(
        text = stringResource(R.string.access_guide_practice_tag),
        style = InteriorTypeVoice.label,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.access_guide_practice_title),
        style = InteriorTypeVoice.screenTitle,
        color = V2Palette.inkPrimary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.access_guide_practice_body),
        style = InteriorTypeVoice.body,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(16.dp))
    PracticeSearchBar(value = input, onValueChange = onInputChange)
    when (status) {
        PracticeStatus.TYPING -> Unit
        PracticeStatus.MISMATCH -> {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_retry),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
        }
        PracticeStatus.MATCHED -> {
            Spacer(modifier = Modifier.height(14.dp))
            PracticeSuccessCard()
            Spacer(modifier = Modifier.height(12.dp))
            // Invitación a la salida real en el rosa de avisos (regla 1).
            Text(
                text = stringResource(R.string.access_guide_practice_exit),
                style = InteriorTypeVoice.body,
                color = V2Palette.accentNotice.stroke,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(V2Corner.control)
                    .background(V2Palette.accentNotice.tint)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * Réplica visual de la barra V2 de la fachada (v2-01): la práctica se ve igual
 * que el uso real, por eso usa el icono meteorológico de búsqueda y no la
 * librería interna.
 */
@Composable
private fun PracticeSearchBar(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = InteriorTypeVoice.body.copy(color = V2Palette.inkPrimary),
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_weather_search),
                contentDescription = null,
                tint = V2Palette.inkSecondary
            )
        },
        singleLine = true,
        // Como en la barra real: sin autocorrección, para que el IME no aprenda
        // ni sugiera después la clave tecleada (RF-14, P-11).
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            imeAction = ImeAction.Search
        ),
        shape = V2Corner.pill,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = V2Palette.cardSurface,
            unfocusedContainerColor = V2Palette.cardSurface,
            focusedTextColor = V2Palette.inkPrimary,
            unfocusedTextColor = V2Palette.inkPrimary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = V2Palette.inkPrimary
        )
    )
}

/** Ingreso de práctica logrado: visto en el verde de «listo» (regla 1). */
@Composable
private fun PracticeSuccessCard() {
    SoftCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(V2Icons.synced),
                contentDescription = null,
                tint = V2Palette.accentCare.stroke,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_done),
                style = InteriorTypeVoice.body,
                color = V2Palette.inkPrimary
            )
        }
    }
}
