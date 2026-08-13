package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureDialog
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2BackButton
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton

/**
 * Ajustes del espacio interno (mockup P21 / v2-23): tres grupos de filas con
 * insignia — Tu llave de entrada, Tu privacidad y Tu cuenta — con la acción
 * delicada (eliminar todo, RF-20) AL FINAL en el rosa de avisos. Menú estático
 * sin ViewModel — mismo criterio que PanelScreen (guía, 2026-07-10): el patrón
 * UiState aplica a pantallas con estado. Los destinos los decide la navegación
 * raíz (guardia RF-06).
 *
 * Matices por significado (regla 1, anexo v2-29 normativo por D7): llave lila
 * para la clave de reconocerte, llave neutra para la contraseña de la cuenta
 * (fila conservada por D6 aunque el mockup la omita), libro azul del tutorial,
 * escudo AZUL de protección para «qué guardamos» (el anexo prima sobre el
 * verdoso del mockup), puerta arena para cerrar sesión y papelera rosa para
 * eliminar. «Cambiar código secreto» del mockup sigue vedado: «Cambiar tu
 * clave personal» (CamouflageResourcesTest, criterio P-17/P-25).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeCode: () -> Unit,
    onChangePassword: () -> Unit,
    onReopenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Solo visibilidad del diálogo: la acción real converge en SessionManager
    // (guía §3: el patrón UiState aplica a pantallas con estado de negocio).
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    // P-15: los botones del diálogo registran interacción — es una ventana
    // propia que el observador de toques del grafo seguro no ve (hallazgo H-1
    // de la auditoría S4, residual pre-S4).
    val sessionManager = LocalSessionManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Cabecera de v2-23: la flecha con el título de pantalla al lado.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V2BackButton(onBack = onBack)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.access_settings_title),
                style = InteriorTypeVoice.screenTitle,
                color = V2Palette.inkPrimary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupLabel(stringResource(R.string.access_settings_group_security))
        SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            IconListRow(
                iconRes = V2Icons.code,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_settings_change_code),
                onClick = onChangeCode,
                trailing = { RowChevron() }
            )
            // RF-39: la contraseña de la cuenta también se cambia desde aquí;
            // la pantalla es de accounts y la conexión, por navegación (§2).
            // Misma llave en neutro: también abre tu espacio, pero no es la
            // clave de reconocerte (fila conservada por D6).
            IconListRow(
                iconRes = V2Icons.code,
                accent = V2Palette.accentGroup,
                title = stringResource(R.string.access_settings_change_password),
                onClick = onChangePassword,
                trailing = { RowChevron() }
            )
            IconListRow(
                iconRes = V2Icons.guide,
                accent = V2Palette.accentProtection,
                title = stringResource(R.string.access_settings_guide),
                onClick = onReopenGuide,
                trailing = { RowChevron() }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupLabel(stringResource(R.string.access_settings_group_privacy))
        SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            IconListRow(
                iconRes = V2Icons.protection,
                accent = V2Palette.accentProtection,
                title = stringResource(R.string.access_settings_privacy),
                onClick = onOpenPrivacy,
                trailing = { RowChevron() }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        SettingsGroupLabel(stringResource(R.string.access_settings_group_account))
        SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            // RF-19: la fila abre la confirmación aquí mismo.
            IconListRow(
                iconRes = V2Icons.signOut,
                accent = V2Palette.accentJustice,
                title = stringResource(R.string.access_settings_sign_out),
                onClick = { showSignOutDialog = true }
            )
            // RF-20: la eliminación tiene pantalla propia (accounts) con su
            // doble confirmación; al final y en rosa por irreversible (v2-23).
            IconListRow(
                iconRes = V2Icons.delete,
                accent = V2Palette.accentNotice,
                title = stringResource(R.string.access_settings_delete_account),
                supportingText = stringResource(R.string.access_settings_delete_support),
                titleColor = V2Palette.accentNotice.stroke,
                onClick = onDeleteAccount,
                trailing = { RowChevron() }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.access_settings_footer),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(88.dp))
    }
    if (showSignOutDialog) {
        SignOutConfirmDialog(
            onConfirm = {
                sessionManager.registerInteraction()
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = {
                sessionManager.registerInteraction()
                showSignOutDialog = false
            }
        )
    }
}

/** Rótulo de grupo de v2-23. */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = InteriorTypeVoice.label,
        color = V2Palette.inkSecondary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/**
 * Confirmación del cierre de sesión (RF-19 criterio 1 de uso consciente): en
 * SecureDialog porque un diálogo es ventana propia y el grafo seguro no lo
 * cubre (RNF-02, regla A1). Sin mensajes al confirmar: el regreso al clima es
 * la única señal, como toda salida (camuflaje F8).
 */
@Composable
private fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SecureDialog(onDismissRequest = onDismiss) {
        SoftCard {
            Text(
                text = stringResource(R.string.access_settings_sign_out_title),
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.access_settings_sign_out_body),
                style = InteriorTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
            Spacer(modifier = Modifier.height(18.dp))
            V2PrimaryButton(
                text = stringResource(R.string.access_settings_sign_out_confirm),
                onClick = onConfirm
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.access_settings_sign_out_cancel),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkSecondary
                )
            }
        }
    }
}
