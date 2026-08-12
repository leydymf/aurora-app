package com.auroraclima.app.core.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Variante de hoja inferior de [SecureDialog] (mockup P20·b, V2): el MISMO mecanismo
 * — una ventana de diálogo con FLAG_SECURE forzada — con el contenido anclado abajo
 * a todo el ancho. No es el ModalBottomSheet de Material: esa API crea su ventana
 * propia sin la bandera y queda vedada fuera de core/security (NoTracesGuardTest).
 * El toque en el fondo atenuado y el botón atrás descartan, como en el diálogo.
 */
@Composable
fun SecureSheet(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // La hoja consume el toque: tocar el contenido no debe descartar.
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
            ) {
                content()
            }
        }
    }
}
