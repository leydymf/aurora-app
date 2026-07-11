package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Tutorial del espacio interno (RF-10; mockups alta-fidelidad-11/12, P07/P07·b).
 * Tres páginas informativas con pager y una práctica final. La práctica de
 * salida es REAL (decisión de la usuaria sobre P07·b y la nota F3·F4 de A2-6):
 * la nube de la esquina — pintada por el grafo seguro — sale de verdad a la
 * fachada, y el reingreso con la clave ejercita RF-12 tal como será siempre.
 *
 * En modo repaso (RF-11, reapertura desde la futura configuración) la única
 * diferencia es el rótulo y que «Atrás» en la primera página regresa a donde
 * estaba la usuaria.
 */
@Composable
fun TutorialRoute(isReview: Boolean, onClose: () -> Unit) {
    val viewModel: TutorialViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    TutorialScreen(
        state = state,
        isReview = isReview,
        onNext = viewModel::onNext,
        onBack = { if (state.isFirstStep) onClose() else viewModel.onBack() },
        onPracticeInputChange = {
            // El teclado es una ventana propia: cada tecla debe contar como
            // interacción o la inactividad expulsaría a mitad de práctica (P-15).
            sessionManager.registerInteraction()
            viewModel.onPracticeInputChange(it)
        }
    )
}

@Composable
internal fun TutorialScreen(
    state: TutorialUiState,
    isReview: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPracticeInputChange: (String) -> Unit,
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
        if (state.step == TutorialStep.PRACTICE) {
            PracticeContent(
                input = state.practiceInput,
                status = state.practiceStatus,
                onInputChange = onPracticeInputChange
            )
        } else {
            InfoPageContent(
                step = state.step,
                isReview = isReview,
                showBack = !state.isFirstStep || isReview,
                onNext = onNext,
                onBack = onBack
            )
        }
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun InfoPageContent(
    step: TutorialStep,
    isReview: Boolean,
    showBack: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    SectionTag(
        stringResource(
            if (isReview) R.string.access_guide_tag_review else R.string.access_guide_tag_first
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
    PagerDots(activeIndex = step.ordinal)
    Spacer(modifier = Modifier.height(16.dp))
    SectionTitle(stringResource(stepTitle(step)))
    Spacer(modifier = Modifier.height(20.dp))
    IllustrationCard(step)
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(stepBody(step)),
        fontFamily = PlexSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = SteelDeep,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    )
    Spacer(modifier = Modifier.height(32.dp))
    PrimaryButton(
        text = stringResource(R.string.access_guide_next),
        onClick = onNext,
        enabled = true,
        isLoading = false
    )
    if (showBack) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.access_guide_back),
                    fontFamily = PlexSans,
                    fontSize = 15.sp,
                    color = SteelDeep
                )
            }
        }
    }
}

@Composable
private fun PracticeContent(input: String, status: PracticeStatus, onInputChange: (String) -> Unit) {
    SectionTag(stringResource(R.string.access_guide_practice_tag))
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle(stringResource(R.string.access_guide_practice_title))
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.access_guide_practice_body),
        fontFamily = PlexSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = SteelDeep
    )
    Spacer(modifier = Modifier.height(16.dp))
    PracticeSearchBar(value = input, onValueChange = onInputChange)
    when (status) {
        PracticeStatus.TYPING -> Unit
        PracticeStatus.MISMATCH -> {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_retry),
                fontFamily = PlexSans,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = SteelDeep
            )
        }
        PracticeStatus.MATCHED -> {
            Spacer(modifier = Modifier.height(14.dp))
            PracticeSuccessCard()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_exit),
                fontFamily = PlexSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Night,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Plum.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

/** Réplica visual de la barra de la fachada: la práctica se ve igual que el uso real. */
@Composable
private fun PracticeSearchBar(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Steel
            )
        },
        singleLine = true,
        // Como en la barra real: sin autocorrección, para que el IME no aprenda
        // ni sugiera después la clave tecleada (RF-14, P-11).
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            imeAction = ImeAction.Search
        ),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CloudWhite,
            unfocusedContainerColor = CloudWhite,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Night
        )
    )
}

@Composable
private fun PracticeSuccessCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudWhite, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_rule_done),
            contentDescription = null,
            tint = Night,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.access_guide_practice_done),
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Night
        )
    }
}

@Composable
private fun PagerDots(activeIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(INFO_PAGE_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == activeIndex) Night else Sky,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Ilustración abstracta de cada página (tarjeta blanca del mockup P07): formas
 * neutras del lenguaje del clima, sin texto que leer ni capturar.
 */
@Composable
private fun IllustrationCard(step: TutorialStep) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(180.dp)
            .background(CloudWhite, RoundedCornerShape(16.dp))
            .border(1.dp, Sky, RoundedCornerShape(16.dp))
    ) {
        when (step) {
            TutorialStep.ENTER -> EnterIllustration()
            TutorialStep.QUICK_EXIT -> QuickExitIllustration()
            TutorialStep.AUTO_CLOSE -> AutoCloseIllustration()
            // La práctica tiene su propio layout y nunca pasa por aquí.
            TutorialStep.PRACTICE -> AutoCloseIllustration()
        }
    }
}

/** Una mini barra con texto abstracto ya tecleado: ahí se escribe la clave. */
@Composable
private fun EnterIllustration() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mist, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Steel,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(SteelDeep, CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        SkeletonLine(widthFraction = 0.7f)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonLine(widthFraction = 0.5f)
    }
}

/** Mini pantalla con la nube resaltada en la esquina, como el mockup P07. */
@Composable
private fun QuickExitIllustration() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column {
            SkeletonLine(widthFraction = 0.8f)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.6f)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.4f)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(44.dp)
                .border(2.dp, Steel, CircleShape)
                .padding(6.dp)
                .background(CloudWhite, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_weather_cloud),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** El clima de vuelta: lo único que queda a la vista. */
@Composable
private fun AutoCloseIllustration() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_weather_cloud_sun),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonLine(widthFraction = 0.55f)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonLine(widthFraction = 0.35f)
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .background(Mist, RoundedCornerShape(5.dp))
    )
}

private fun stepTitle(step: TutorialStep) = when (step) {
    TutorialStep.ENTER -> R.string.access_guide_enter_title
    TutorialStep.QUICK_EXIT -> R.string.access_guide_exit_title
    TutorialStep.AUTO_CLOSE -> R.string.access_guide_close_title
    // La práctica tiene su propio layout y nunca pasa por aquí.
    TutorialStep.PRACTICE -> R.string.access_guide_practice_title
}

private fun stepBody(step: TutorialStep) = when (step) {
    TutorialStep.ENTER -> R.string.access_guide_enter_body
    TutorialStep.QUICK_EXIT -> R.string.access_guide_exit_body
    TutorialStep.AUTO_CLOSE -> R.string.access_guide_close_body
    // La práctica tiene su propio layout y nunca pasa por aquí.
    TutorialStep.PRACTICE -> R.string.access_guide_practice_body
}

private const val INFO_PAGE_COUNT = 3
