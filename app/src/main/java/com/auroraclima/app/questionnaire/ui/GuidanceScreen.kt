package com.auroraclima.app.questionnaire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.ErrorState
import com.auroraclima.app.core.ui.components.LoadingState
import com.auroraclima.app.core.ui.components.PrimaryButton
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Question

/**
 * Cuestionario orientador (RF-27; mockups P15/P16 — alta-fidelidad-24/25).
 * Vive dentro del grafo seguro: bloqueo de capturas y recientes, nube de
 * salida y reporte de interacción (toques y scroll) llegan por construcción
 * (RNF-02/03, P-21).
 * No hay campos de texto: la regla P-15 no aplica a estas pantallas.
 *
 * Los textos de preguntas, escala y avisos llegan del documento administrable
 * y nunca van en el APK (RNF-05); los estáticos usan claves neutras
 * `guidance_*` vigiladas por CamouflageResourcesTest.
 */
@Composable
fun GuidanceRoute(onClose: () -> Unit, onOpenRoutes: () -> Unit) {
    val viewModel: GuidanceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val dismiss = {
        viewModel.onAbandon()
        onClose()
    }
    // El botón atrás del sistema también retrocede pregunta a pregunta
    // (RF-27 criterio 1); en la portada sigue el pop normal hacia el panel y
    // en el resultado equivale a «Terminar»: descartar y volver (RF-31 es
    // trabajo futuro — no hay nada que conservar).
    BackHandler(enabled = state.phase != GuidancePhase.INTRO) {
        when (state.phase) {
            GuidancePhase.QUESTION -> viewModel.onPrevious()
            GuidancePhase.RESULT -> dismiss()
            GuidancePhase.INTRO -> Unit
        }
    }
    GuidanceScreen(
        state = state,
        onStart = viewModel::onStart,
        onDismiss = dismiss,
        onOptionSelected = viewModel::onOptionSelected,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onOpenRoutes = onOpenRoutes,
        onRetry = viewModel::onRetry
    )
}

@Composable
internal fun GuidanceScreen(
    state: GuidanceUiState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenRoutes: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
    ) {
        when {
            state.isLoading -> LoadingState()
            state.errorRes != null -> ErrorState(messageRes = R.string.common_error_generic, onRetry = onRetry)
            else -> when (state.phase) {
                GuidancePhase.INTRO -> IntroContent(state = state, onStart = onStart, onDismiss = onDismiss)
                GuidancePhase.QUESTION -> QuestionContent(
                    state = state,
                    onOptionSelected = onOptionSelected,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onAbandon = onDismiss
                )
                GuidancePhase.RESULT -> ResultContent(
                    state = state,
                    onOpenRoutes = onOpenRoutes,
                    onFinish = onDismiss
                )
            }
        }
    }
}

/** Portada del cuestionario (P15): título e introducción del documento, reglas y aviso. */
@Composable
private fun IntroContent(state: GuidanceUiState, onStart: () -> Unit, onDismiss: () -> Unit) {
    val questionnaire = state.questionnaire ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.guidance_back),
                    tint = Night
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            SectionTag(stringResource(R.string.guidance_tag))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(questionnaire.title)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = questionnaire.intro,
                fontFamily = PlexSans,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = SteelDeep
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CloudWhite
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GuidanceBullet(stringResource(R.string.guidance_intro_point_pace))
                    GuidanceBullet(stringResource(R.string.guidance_intro_point_discard))
                    GuidanceBullet(stringResource(R.string.guidance_intro_point_once))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            NoticeCard(questionnaire.notice)
            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            PrimaryButton(
                text = stringResource(R.string.guidance_start),
                onClick = onStart,
                enabled = true,
                isLoading = false
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.guidance_not_now),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = SteelDeep
                )
            }
            Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
        }
    }
}

/** Una pregunta por pantalla (P16): progreso, opciones de la escala y navegación. */
@Composable
private fun QuestionContent(
    state: GuidanceUiState,
    onOptionSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAbandon: () -> Unit
) {
    val questionnaire = state.questionnaire ?: return
    val question = state.currentQuestion ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTag(
                stringResource(
                    R.string.guidance_question_tag,
                    state.questionIndex + 1,
                    questionnaire.questions.size
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAbandon) {
                Text(
                    text = stringResource(R.string.guidance_abandon),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SteelDeep
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (state.questionIndex + 1f) / questionnaire.questions.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = SteelDeep,
            trackColor = Sky
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            QuestionText(question)
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                questionnaire.scale.forEach { option ->
                    ScaleOptionRow(
                        label = option.label,
                        selected = option.id == state.selectedOptionId,
                        onClick = { onOptionSelected(option.id) }
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Sky),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CloudWhite,
                    contentColor = SteelDeep
                )
            ) {
                Text(
                    text = stringResource(R.string.guidance_previous),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            PrimaryButton(
                text = stringResource(
                    if (state.isLastQuestion) R.string.guidance_see_result else R.string.guidance_next
                ),
                onClick = onNext,
                enabled = true,
                isLoading = false,
                modifier = Modifier.weight(1.6f)
            )
        }
        Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
    }
}

/**
 * Resultado orientativo (RF-29, mockup P17). La etiqueta, el nivel y el aviso
 * de alcance van en la zona FIJA de la columna — fuera del área desplazable —
 * para que el aviso sea visible en todo resultado sin necesidad de scroll
 * (RF-30 criterio 1, «visible en 360×800 sin desplazarse» de F6); solo las
 * recomendaciones y el acceso a rutas se desplazan.
 *
 * «Guardar resultado» del mockup no se pinta: RF-31 queda como trabajo futuro
 * y el resultado se descarta siempre al terminar.
 */
@Composable
private fun ResultContent(state: GuidanceUiState, onOpenRoutes: () -> Unit, onFinish: () -> Unit) {
    val questionnaire = state.questionnaire ?: return
    val result = state.result ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        SectionTag(stringResource(R.string.guidance_result_tag))
        Spacer(modifier = Modifier.height(12.dp))
        LevelCard(level = result.level)
        Spacer(modifier = Modifier.height(10.dp))
        NoticeCard(questionnaire.notice)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (result.answeredCount < result.questionCount) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.guidance_result_partial,
                        result.answeredCount,
                        result.questionCount
                    ),
                    fontFamily = PlexSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = SteelDeep
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            SectionTag(stringResource(R.string.guidance_result_actions))
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.level.advice.forEach { advice -> GuidanceBullet(advice) }
            }
            if (result.level.routeIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                RoutesLinkRow(onClick = onOpenRoutes)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        PrimaryButton(
            text = stringResource(R.string.guidance_result_finish),
            onClick = onFinish,
            enabled = true,
            isLoading = false
        )
        Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
    }
}

/**
 * Tarjeta del nivel: el nombre del nivel es el protagonista tipográfico y una
 * franja lateral lo acompaña con un acento POR NIVEL tomado solo de la paleta
 * serena de la marca — sin semáforos ni rojos de alarma (ajuste de la usuaria
 * 2026-07-13 sobre la decisión F6 de A2-6, anotado en P-41: F6 mantiene «el
 * nivel se dice con palabras» y el acento gradúa dentro del mismo cielo).
 */
@Composable
private fun LevelCard(level: GuidanceLevel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CloudWhite
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(levelAccent(level.order))
            )
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTag(stringResource(R.string.guidance_result_level))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = level.title,
                    fontFamily = Marcellus,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    color = Night
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = level.summary,
                    fontFamily = PlexSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Night
                )
            }
        }
    }
}

/**
 * Acento tonal por severidad, acotado a la paleta existente: el `order` del
 * nivel llega del documento administrable y puede crecer (RF-43), así que la
 * escala se satura en Ciruela para cualquier severidad por encima de la cuarta.
 */
private fun levelAccent(order: Int): Color = when {
    order <= 1 -> Sky
    order == 2 -> Steel
    order == 3 -> SteelDeep
    else -> Plum
}

/** Acceso a las rutas de atención (RF-29 criterio 2): navegación, nunca import de content (§2). */
@Composable
private fun RoutesLinkRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.guidance_result_routes),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Night,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}

@Composable
private fun QuestionText(question: Question) {
    Text(
        text = question.text,
        fontFamily = Marcellus,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = Night
    )
}

/** Opción de la escala como fila selectable: la tarjeta completa es el objetivo táctil. */
@Composable
private fun ScaleOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) SteelDeep else Sky,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = SteelDeep,
                    unselectedColor = Steel
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontFamily = PlexSans,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp,
                color = Night
            )
        }
    }
}

/** Punto de viñeta + texto (tarjeta de reglas de P15). */
@Composable
private fun GuidanceBullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Steel)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Night
        )
    }
}

/** Aviso destacado en ciruela serena (sin rojo de alarma, decisión F2/F6 de A2-6). */
@Composable
private fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Plum.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontFamily = PlexSans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Night,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

// Carga y error genérico con reintento: LoadingState/ErrorState de
// core/ui/components desde P-42a (P-24, guía §3).

/** Espacio final para que los botones no queden bajo la nube de la esquina (RF-16). */
private val GUIDANCE_BOTTOM_CLEARANCE = 88.dp
