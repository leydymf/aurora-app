package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.SecureDialog
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Configuración del espacio interno (mockup P21 / alta-fidelidad-33): grupos de
 * filas con navegación. Menú estático sin ViewModel — mismo criterio que
 * PanelScreen (guía, 2026-07-10): el patrón UiState aplica a pantallas con
 * estado. Los destinos los decide la navegación raíz (guardia RF-06).
 *
 * Redacción de camuflaje sobre el mockup (mismo criterio que P-17/P-25):
 * «Cambiar código secreto» → «Cambiar tu clave personal» («secreto» está vedado
 * en recursos por CamouflageResourcesTest). La fila «Eliminar cuenta y datos»
 * llegará con RF-20.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeCode: () -> Unit,
    onReopenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Solo visibilidad del diálogo: la acción real converge en SessionManager
    // (guía §3: el patrón UiState aplica a pantallas con estado de negocio).
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BackHeader(tag = stringResource(R.string.access_settings_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.access_settings_title))
        Spacer(modifier = Modifier.height(20.dp))

        SectionTag(stringResource(R.string.access_settings_group_security))
        Spacer(modifier = Modifier.height(10.dp))
        SettingsGroup {
            SettingsRow(
                icon = rememberVectorPainter(Icons.Outlined.Lock),
                title = stringResource(R.string.access_settings_change_code),
                onClick = onChangeCode
            )
            SettingsRowDivider()
            SettingsRow(
                icon = painterResource(R.drawable.ic_book),
                title = stringResource(R.string.access_settings_guide),
                onClick = onReopenGuide
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        SectionTag(stringResource(R.string.access_settings_group_privacy))
        Spacer(modifier = Modifier.height(10.dp))
        SettingsGroup {
            SettingsRow(
                icon = rememberVectorPainter(Icons.Outlined.Info),
                title = stringResource(R.string.access_settings_privacy),
                onClick = onOpenPrivacy
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        SectionTag(stringResource(R.string.access_settings_group_account))
        Spacer(modifier = Modifier.height(10.dp))
        SettingsGroup {
            // RF-19: la fila abre la confirmación; «Eliminar cuenta» llegará con RF-20.
            SettingsRow(
                icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.ExitToApp),
                title = stringResource(R.string.access_settings_sign_out),
                onClick = { showSignOutDialog = true },
                showChevron = false
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.access_settings_footer),
            fontFamily = PlexSans,
            fontSize = 12.sp,
            color = Steel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(64.dp))
    }
    if (showSignOutDialog) {
        SignOutConfirmDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
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
        Surface(shape = RoundedCornerShape(20.dp), color = CloudWhite) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    text = stringResource(R.string.access_settings_sign_out_title),
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Night
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.access_settings_sign_out_body),
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SteelDeep
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.access_settings_sign_out_cancel),
                            fontFamily = PlexSans,
                            fontSize = 15.sp,
                            color = SteelDeep
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Night,
                            contentColor = CloudWhite
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.access_settings_sign_out_confirm),
                            fontFamily = PlexSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/** Tarjeta blanca que agrupa filas, con separadores suaves (mockup P21). */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(color = Mist, thickness = 1.dp)
}

/** Fila de configuración: ícono lineal, título y chevron de navegación. */
@Composable
internal fun SettingsRow(
    icon: Painter,
    title: String,
    onClick: () -> Unit,
    titleColor: Color = Night,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = SteelDeep,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}
