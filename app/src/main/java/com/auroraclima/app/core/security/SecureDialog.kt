package com.auroraclima.app.core.security

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * Único diálogo permitido en el espacio interno (RNF-02, auditoría A1): los diálogos,
 * popups y bottom sheets crean una ventana PROPIA que no es la de la Activity, así que
 * el grafo seguro no los cubre; aquí la bandera se fuerza en la ventana del diálogo.
 * NoTracesGuardTest veda las APIs de ventana propia fuera de core/security; si una
 * pantalla interna necesita otra variante (bottom sheet, popup), el envoltorio se
 * agrega aquí con la misma política.
 */
@Composable
fun SecureDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        content = content
    )
}
