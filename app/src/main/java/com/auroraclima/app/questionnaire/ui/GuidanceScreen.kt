package com.auroraclima.app.questionnaire.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.directory.DirectoryEntity
import com.auroraclima.app.core.directory.DirectoryLine
import com.auroraclima.app.core.security.SecureSheet
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentChip
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SegmentedProgressBar
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackButton
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2ErrorState
import com.auroraclima.app.core.ui.v2.V2FieldMessage
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2LoadingState
import com.auroraclima.app.core.ui.v2.V2NoticeCard
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecondaryButton
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Municipality
import com.auroraclima.app.questionnaire.domain.model.Question

/**
 * Cuestionario orientador validado (RF-27; documento §3–§6). Vive dentro del
 * grafo seguro: bloqueo de capturas y recientes, nube de salida y reporte de
 * interacción (toques y scroll) llegan por construcción (RNF-02/03, P-21).
 * No hay campos de texto: la regla P-15 no aplica a estas pantallas.
 *
 * Los textos de preguntas, escala, bloques, aviso, reconocimiento y niveles
 * llegan del documento administrable y nunca van en el APK (RNF-05); los
 * estáticos usan claves neutras `guidance_*` vigiladas por CamouflageResourcesTest.
 *
 * V2 (S4 de PLAN_UI_V2.md; mockups v2-17/18/19): re-estilo puro sobre los tres
 * estados existentes — el motor, el ViewModel y el descarte en memoria (RF-28)
 * no cambian. Desde el 2026-08-14 el resultado ofrece además el guardado
 * opcional de RF-31: opt-in expreso vía hoja segura; sin tocarlo, terminar
 * sigue descartando todo, como siempre.
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
    // en el resultado equivale a «Terminar»: descarta lo NO guardado (RF-31
    // criterio 1) y vuelve. Con la hoja de guardado abierta, el back lo
    // intercepta la propia hoja (SecureSheet) y solo la cierra.
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
        onMunicipalitySelected = viewModel::onMunicipalitySelected,
        onOpenRoutes = onOpenRoutes,
        onRetry = viewModel::onRetry,
        onSaveRequested = viewModel::onSaveRequested,
        onSaveConfirmed = viewModel::onSaveConfirmed,
        onSaveDismissed = viewModel::onSaveDismissed
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
    onMunicipalitySelected: (String) -> Unit,
    onOpenRoutes: () -> Unit,
    onRetry: () -> Unit,
    onSaveRequested: () -> Unit,
    onSaveConfirmed: () -> Unit,
    onSaveDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
    ) {
        when {
            state.isLoading -> V2LoadingState()
            state.errorRes != null -> V2ErrorState(messageRes = state.errorRes, onRetry = onRetry)
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
                    onMunicipalitySelected = onMunicipalitySelected,
                    onOpenRoutes = onOpenRoutes,
                    onFinish = onDismiss,
                    onSaveRequested = onSaveRequested
                )
            }
        }
    }
    if (state.isSaveSheetVisible) {
        SaveConfirmSheet(
            isSaving = state.isSaving,
            saveErrorRes = state.saveErrorRes,
            onConfirm = onSaveConfirmed,
            onDismiss = onSaveDismissed
        )
    }
}

/**
 * Portada del cuestionario (P15, v2-17): título e introducción del documento,
 * las tres garantías con icono y el aviso de alcance. Los matices de las
 * garantías siguen el mockup y la regla 1: verde de cuidado para el ritmo
 * propio, azul de protección para el descarte, arena de tiempo para la única
 * vez; el aviso va en el rosa de avisos (RF-30 también en la portada).
 */
@Composable
private fun IntroContent(state: GuidanceUiState, onStart: () -> Unit, onDismiss: () -> Unit) {
    val questionnaire = state.questionnaire ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        V2BackHeader(
            tag = stringResource(R.string.guidance_tag),
            onBack = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentBadge(iconRes = V2Icons.recognize, accent = V2Palette.accentRecognize)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = questionnaire.title,
                    style = InteriorTypeVoice.screenTitle,
                    color = V2Palette.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = questionnaire.intro,
                style = InteriorTypeVoice.body,
                color = V2Palette.inkSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            SoftCard {
                IconListRow(
                    iconRes = V2Icons.waiting,
                    accent = V2Palette.accentCare,
                    title = stringResource(R.string.guidance_intro_point_pace_title),
                    supportingText = stringResource(R.string.guidance_intro_point_pace_body)
                )
                IconListRow(
                    iconRes = V2Icons.delete,
                    accent = V2Palette.accentProtection,
                    title = stringResource(R.string.guidance_intro_point_discard_title),
                    supportingText = stringResource(R.string.guidance_intro_point_discard_body)
                )
                IconListRow(
                    iconRes = V2Icons.info,
                    accent = V2Palette.accentJustice,
                    title = stringResource(R.string.guidance_intro_point_once_title),
                    supportingText = stringResource(R.string.guidance_intro_point_once_body)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            V2NoticeCard(questionnaire.notice)
            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            V2PrimaryButton(text = stringResource(R.string.guidance_start), onClick = onStart)
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.guidance_not_now),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkSecondary
                )
            }
            Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
        }
    }
}

/**
 * Una pregunta por pantalla (P16, v2-18), con el título de su bloque como
 * encabezado (los dos bloques del documento, §3): «Pregunta N de M» + «Salir»
 * siempre visibles, progreso por segmentos SIEMPRE junto al texto que enuncia
 * el avance (regla 3), opciones de la escala como tarjetas y navegación abajo.
 * La pregunta de contexto P0 usa su escala de vínculo, el resto la de
 * frecuencia o la de percepción.
 */
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
            Text(
                text = stringResource(
                    R.string.guidance_question_tag,
                    state.questionIndex + 1,
                    questionnaire.questions.size
                ),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary,
                modifier = Modifier.weight(1f)
            )
            ExitChip(onClick = onAbandon)
        }
        Spacer(modifier = Modifier.height(12.dp))
        SegmentedProgressBar(
            totalSegments = questionnaire.questions.size,
            completedSegments = state.questionIndex + 1
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            state.currentBlock?.let { block ->
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = block.title,
                    style = InteriorTypeVoice.label,
                    color = V2Palette.inkSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            QuestionText(question)
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.currentOptions.forEach { option ->
                    ScaleOptionRow(
                        label = option.label,
                        selected = option.id == state.selectedOptionId,
                        onClick = { onOptionSelected(option.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V2BackButton(onBack = onPrevious)
            Spacer(modifier = Modifier.width(12.dp))
            V2PrimaryButton(
                text = stringResource(
                    if (state.isLastQuestion) R.string.guidance_see_result else R.string.guidance_next
                ),
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
    }
}

/**
 * Chip «Salir» de P16 (RF-28): píldora blanca con la equis de mobiliario y el
 * contorno gris de control, porque va directo sobre el fondo lila (sin borde,
 * 1.12:1; WCAG §1.4.11 pide ≥ 3:1).
 */
@Composable
private fun ExitChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(V2Corner.pill)
            .background(V2Palette.cardSurface)
            .border(width = 1.dp, color = V2Palette.controlOutline, shape = V2Corner.pill)
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(V2Icons.close),
            contentDescription = null,
            tint = V2Palette.inkPrimary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.guidance_abandon),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkPrimary
        )
    }
}

/**
 * Resultado orientativo (P17, v2-19; RF-29, documento §6). Jerarquía de la
 * decisión directora 2026-08-14 (reversión informada de P-41, acta §3.2): la
 * tarjeta del NIVEL es lo primero y lo dominante — nombre en palabras con su
 * color semafórico — y en la zona FIJA de la columna, seguida del aviso de
 * alcance (RF-30) también fijo, fuera del área desplazable: el aviso es visible
 * en todo resultado sin necesidad de scroll (RF-30 criterio 1). Subordinados y
 * desplazables, en este orden: frase del nivel, tipos detectados
 * (reconocimiento), recomendaciones y accesos por municipio.
 *
 * Al final del contenido va «Guardar este resultado» (RF-31): opt-in expreso
 * que abre la hoja segura de confirmación. Sin tocarlo, nada persiste y
 * «Terminar» — siempre fijo — descarta como hasta ahora (criterio 1).
 */
@Composable
private fun ResultContent(
    state: GuidanceUiState,
    onMunicipalitySelected: (String) -> Unit,
    onOpenRoutes: () -> Unit,
    onFinish: () -> Unit,
    onSaveRequested: () -> Unit
) {
    val questionnaire = state.questionnaire ?: return
    val result = state.result ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.guidance_result_tag),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        LevelCard(level = result.level)
        Spacer(modifier = Modifier.height(10.dp))
        V2NoticeCard(questionnaire.notice)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = result.level.summary,
                style = InteriorTypeVoice.body,
                color = V2Palette.inkPrimary
            )
            state.recognitionText?.let { recognition ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = recognition,
                    style = InteriorTypeVoice.body,
                    color = V2Palette.inkPrimary
                )
            }
            if (result.answeredCount < result.questionCount) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.guidance_result_partial,
                        result.answeredCount,
                        result.questionCount
                    ),
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.guidance_result_actions),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            SoftCard {
                result.level.advice.forEach { advice -> AdviceRow(advice) }
            }
            AccessSection(
                state = state,
                onMunicipalitySelected = onMunicipalitySelected
            )
            Spacer(modifier = Modifier.height(18.dp))
            RoutesLinkRow(onClick = onOpenRoutes)
            Spacer(modifier = Modifier.height(18.dp))
            if (state.isSaved) {
                AccentChip(
                    text = stringResource(R.string.guidance_result_saved),
                    accent = V2Palette.accentCare,
                    iconRes = V2Icons.synced,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                V2SecondaryButton(
                    text = stringResource(R.string.guidance_result_save),
                    onClick = onSaveRequested
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        V2PrimaryButton(text = stringResource(R.string.guidance_result_finish), onClick = onFinish)
        Spacer(modifier = Modifier.height(GUIDANCE_BOTTOM_CLEARANCE))
    }
}

/**
 * Confirmación expresa del guardado (RF-31 criterio 2; mismo patrón de hoja
 * segura que la eliminación del historial): enumera EXACTAMENTE lo que se
 * guardará — fecha, nivel y tipos reconocidos, nunca las respuestas — antes de
 * persistir nada. «Guardar» es la única acción llena (regla 2); cancelar o
 * tocar afuera no deja rastro. La hoja es [SecureSheet], la variante de hoja
 * de SecureDialog (RNF-02): las APIs con ventana propia solo se usan a través
 * de los envoltorios de core/security.
 */
@Composable
private fun SaveConfirmSheet(isSaving: Boolean, saveErrorRes: Int?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SecureSheet(onDismissRequest = onDismiss) {
        V2SheetCard {
            AccentBadge(iconRes = V2Icons.synced, accent = V2Palette.accentCare)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.guidance_save_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guidance_save_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary,
                textAlign = TextAlign.Center
            )
            V2FieldMessage(messageRes = saveErrorRes, fallbackRes = null)
            Spacer(modifier = Modifier.height(18.dp))
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(22.dp),
                    color = V2Palette.accentCare.stroke,
                    strokeWidth = 2.dp
                )
            } else {
                V2PrimaryButton(
                    text = stringResource(R.string.guidance_save_confirm),
                    onClick = onConfirm
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(
                    text = stringResource(R.string.guidance_save_cancel),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Accesos directos del nivel (§6): líneas nacionales y entidades fijas, luego el
 * selector de municipio y las entidades de ese municipio. Las líneas y entidades
 * llegan resueltas del contrato de core; un id sin resolver simplemente no
 * aparece (RF-42).
 */
@Composable
private fun AccessSection(state: GuidanceUiState, onMunicipalitySelected: (String) -> Unit) {
    val questionnaire = state.questionnaire ?: return
    val level = state.result?.level ?: return
    val hasAny = level.lineIds.isNotEmpty() || level.agencyIds.isNotEmpty() || level.municipalAgencyIds.isNotEmpty()
    if (!hasAny) return

    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.guidance_result_access),
        style = InteriorTypeVoice.label,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.nationalLines.forEach { line -> LineRow(line) }
        state.fixedEntities.forEach { entity -> EntityRow(entity) }
    }
    if (level.municipalAgencyIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.guidance_result_municipality_prompt),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            questionnaire.municipalities.forEach { municipality ->
                MunicipalityRow(
                    municipality = municipality,
                    selected = municipality.id == state.selectedMunicipalityId,
                    onClick = { onMunicipalitySelected(municipality.id) }
                )
            }
        }
        val municipalEntities = state.municipalEntities
        if (municipalEntities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                municipalEntities.forEach { entity -> EntityRow(entity) }
            }
        }
    }
}

/**
 * Tarjeta del nivel (v2-19 adaptado): el nombre del nivel sigue siendo el
 * protagonista tipográfico — «el nivel se dice con palabras» (decisión F6) —
 * y desde la decisión directora 2026-08-14 (reversión informada de P-41, acta
 * §3.2) lo acompaña su color semafórico representativo: fondo en el tinte y
 * texto en el trazo del par de severidad que corresponde al orden del nivel.
 * El color refuerza pero nunca informa solo (regla 3): las palabras validadas
 * siguen encima, y el par cumple AA (PaletteContrastTest).
 */
@Composable
private fun LevelCard(level: GuidanceLevel) {
    val accent = severityAccent(level.order)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(V2Corner.card)
            .background(accent.tint)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(V2Corner.pill)
                .background(accent.stroke)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(R.string.guidance_result_level),
                style = InteriorTypeVoice.label,
                color = accent.stroke
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = level.title,
                style = InteriorTypeVoice.screenTitle,
                color = accent.stroke
            )
        }
    }
}

/** Acceso a las rutas de atención completas (RF-29 criterio 2): navegación, nunca import de content (§2). */
@Composable
private fun RoutesLinkRow(onClick: () -> Unit) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        IconListRow(
            iconRes = V2Icons.place,
            accent = V2Palette.accentGroup,
            title = stringResource(R.string.guidance_result_routes),
            onClick = onClick,
            trailing = { RowChevron() }
        )
    }
}

@Composable
private fun QuestionText(question: Question) {
    Text(
        text = question.text,
        style = InteriorTypeVoice.screenTitle,
        color = V2Palette.inkPrimary
    )
}

/**
 * Opción de la escala como tarjeta selectable (v2-18): la tarjeta completa es
 * el objetivo táctil; la elegida lleva el borde y el visto en el trazo lila de
 * reconocer — contorno, nunca relleno (regla 4) — y además el peso tipográfico:
 * el color nunca informa solo (regla 3).
 */
@Composable
private fun ScaleOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(V2Corner.card)
            .background(V2Palette.cardSurface)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = V2Palette.accentRecognize.stroke,
                        shape = V2Corner.card
                    )
                } else {
                    Modifier
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                painter = painterResource(V2Icons.synced),
                contentDescription = null,
                tint = V2Palette.accentRecognize.stroke,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(width = 1.5.dp, color = V2Palette.inkSecondary, shape = CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = if (selected) InteriorTypeVoice.bodyStrong else InteriorTypeVoice.body,
            color = V2Palette.inkPrimary
        )
    }
}

/** Selección de municipio para los accesos (§6): misma tarjeta selectable que la escala. */
@Composable
private fun MunicipalityRow(municipality: Municipality, selected: Boolean, onClick: () -> Unit) {
    ScaleOptionRow(label = municipality.label, selected = selected, onClick = onClick)
}

/**
 * Línea de atención: toca la fila para abrir el marcador con el número puesto
 * (RF-23, decisión F5); ACTION_DIAL no exige permiso (RNF-04) y no marca solo.
 */
@Composable
private fun LineRow(line: DirectoryLine) {
    val context = LocalContext.current
    ContactCard(
        title = line.name,
        subtitle = line.number,
        onCall = { dialNumber(context, line.number) }
    )
}

/** Entidad: toca la fila para llamar al primer número disponible (§6). */
@Composable
private fun EntityRow(entity: DirectoryEntity) {
    val context = LocalContext.current
    val number = entity.phones.firstOrNull()
    ContactCard(
        title = entity.name,
        subtitle = entity.address ?: entity.role,
        onCall = number?.let { { dialNumber(context, it) } }
    )
}

/** Contacto llamable: insignia verde de llamar (regla 1) y la fila entera como destino. */
@Composable
private fun ContactCard(title: String, subtitle: String, onCall: (() -> Unit)?) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        IconListRow(
            iconRes = V2Icons.call,
            accent = V2Palette.accentCare,
            title = title,
            supportingText = subtitle,
            onClick = onCall,
            iconDescription = if (onCall != null) stringResource(R.string.guidance_call) else null
        )
    }
}

/** Recomendación del nivel (§6): texto administrable con la marca de viñeta neutra. */
@Composable
private fun AdviceRow(text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(V2Palette.accentRecognize.stroke)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = InteriorTypeVoice.body,
            color = V2Palette.inkPrimary
        )
    }
}

/**
 * Abre el marcador con el número puesto (RF-23, decisión F5): ACTION_DIAL no
 * exige permiso (RNF-04), no marca por sí solo y la app no conserva rastro. Sin
 * marcador instalado la acción se descarta en silencio (mismo criterio que el
 * único punto de salida de content, D4 S2 B-1). El cuestionario lo replica sin
 * importar content (§2), igual que la barra de práctica del tutorial.
 */
private fun dialNumber(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
}

/** Espacio final para que los botones no queden bajo la nube de la esquina (RF-16). */
private val GUIDANCE_BOTTOM_CLEARANCE = 88.dp
