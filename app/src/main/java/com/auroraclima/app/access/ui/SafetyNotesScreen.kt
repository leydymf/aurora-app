package com.auroraclima.app.access.ui

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.CloudReturnClearance
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2BackHeader
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette

/**
 * «Seguridad en Aurora» (v0.3.4, Ajustes › Tu acceso): cómo protege Aurora a la
 * usuaria, en filas cortas con icono. Mismo esquema que «Qué guardamos y por qué»
 * (`PrivacyScreen`): texto fijo, sin ViewModel ni estado. Cada frase se comprobó
 * contra el código (`SessionManager`, `SecureNavigation`, `ImageImporter`,
 * `CaptureBuffer`, la sincronización cifrada y `DeleteAccountUseCase`); si uno de
 * esos comportamientos cambia, esta pantalla cambia con él.
 */
@Composable
fun SafetyNotesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        V2BackHeader(tag = stringResource(R.string.access_notes_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.access_notes_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(20.dp))
        IconNoteCard(SAFETY_NOTES, rowSpacing = 10.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.access_notes_more),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
        // Aire final: la última línea sube por encima de la nube de la esquina.
        Spacer(modifier = Modifier.height(CloudReturnClearance))
    }
}

/** Las diez filas, en el orden aprobado por la autora; cada par icono/acento es el fijo de D2. */
private val SAFETY_NOTES = listOf(
    IconNote(
        iconRes = V2Icons.cloud,
        accent = V2Palette.accentCloud,
        title = R.string.access_notes_cloud_title,
        body = R.string.access_notes_cloud_body
    ),
    IconNote(
        iconRes = V2Icons.code,
        accent = V2Palette.accentRecognize,
        title = R.string.access_notes_enter_title,
        body = R.string.access_notes_enter_body
    ),
    IconNote(
        iconRes = V2Icons.slashCircle,
        accent = V2Palette.accentGroup,
        title = R.string.access_notes_wrong_title,
        body = R.string.access_notes_wrong_body
    ),
    IconNote(
        iconRes = V2Icons.waiting,
        accent = V2Palette.accentJustice,
        title = R.string.access_notes_idle_title,
        body = R.string.access_notes_idle_body
    ),
    IconNote(
        iconRes = V2Icons.device,
        accent = V2Palette.accentGroup,
        title = R.string.access_notes_captures_title,
        body = R.string.access_notes_captures_body
    ),
    IconNote(
        iconRes = V2Icons.recognize,
        accent = V2Palette.accentRecognize,
        title = R.string.access_notes_recognize_title,
        body = R.string.access_notes_recognize_body
    ),
    IconNote(
        iconRes = V2Icons.protection,
        accent = V2Palette.accentProtection,
        title = R.string.access_notes_device_title,
        body = R.string.access_notes_device_body
    ),
    IconNote(
        iconRes = V2Icons.synced,
        accent = V2Palette.accentCare,
        title = R.string.access_notes_account_title,
        body = R.string.access_notes_account_body
    ),
    IconNote(
        iconRes = V2Icons.media,
        accent = V2Palette.accentGroup,
        title = R.string.access_notes_files_title,
        body = R.string.access_notes_files_body
    ),
    IconNote(
        iconRes = V2Icons.delete,
        accent = V2Palette.accentNotice,
        title = R.string.access_notes_delete_title,
        body = R.string.access_notes_delete_body
    )
)
