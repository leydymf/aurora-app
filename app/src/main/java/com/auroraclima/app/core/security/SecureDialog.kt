package com.auroraclima.app.core.security

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import com.auroraclima.app.core.ui.v2.keepReleasedFocusOutOfFields

/**
 * Único diálogo permitido en el espacio interno (RNF-02, auditoría A1): los diálogos,
 * popups y bottom sheets crean una ventana PROPIA que no es la de la Activity, así que
 * el grafo seguro no los cubre; aquí la bandera se fuerza en la ventana del diálogo.
 * NoTracesGuardTest veda las APIs de ventana propia fuera de core/security; si una
 * pantalla interna necesita otra variante (bottom sheet, popup), el envoltorio se
 * agrega aquí con la misma política.
 *
 * El contenido se desplaza (v0.3.2): con el teclado abierto la plataforma encoge la
 * ventana del diálogo, y en la confirmación con contraseña de RF-20 el botón
 * «Eliminar definitivamente» quedaba cortado debajo (corrida en el emulador). Por eso
 * el contenido no puede llevar su propia lista perezosa ni otro desplazamiento
 * vertical (lo primero lo vigila `KeyboardAwareGuardTest`). En API 26 la plataforma
 * no achicaba esa ventana por su cuenta: se le pide `ADJUST_RESIZE` explícito.
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
        )
    ) {
        // El foco soltado no debe volver al campo (API 26–27, KeyboardAware.kt).
        SetUpDialogWindow { window ->
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.keepReleasedFocusOutOfFields()
        }
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
    }
}

/**
 * Variante de hoja inferior de [SecureDialog] (mockup P20·b, V2): el MISMO mecanismo
 * — una ventana de diálogo con FLAG_SECURE forzada — con el contenido anclado abajo
 * a todo el ancho. No es el ModalBottomSheet de Material: esa API crea su ventana
 * propia sin la bandera y queda vedada fuera de core/security (NoTracesGuardTest).
 * El toque en el fondo atenuado y el botón atrás descartan, como en el diálogo.
 *
 * El teclado (v0.3.2): la ventana de la hoja no se achicaba al abrirlo y tapaba el
 * campo de correo de RF-46 y el botón de envío (corrida en el emulador); pedirle
 * `ADJUST_RESIZE` no bastó en API 30. Por eso la ventana pasa a ocupar la pantalla
 * entera (`decorFitsSystemWindows = false`) y la hoja aparta ella misma la barra de
 * estado y el teclado; si el contenido no cabe, se desplaza. Por lo mismo, como en
 * [SecureDialog], el contenido no puede llevar su propia lista perezosa (lo vigila
 * `KeyboardAwareGuardTest`). Con esa ventana se perdía el fondo atenuado, que se
 * vuelve a pedir explícito (`FLAG_DIM_BEHIND`). La bandera (`SecureOn`) no cambia.
 */
@Composable
fun SecureSheet(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn,
            decorFitsSystemWindows = false
        )
    ) {
        // Repone el fondo atenuado que esta ventana a pantalla completa perdía. Solo
        // suma FLAG_DIM_BEHIND; no toca FLAG_SECURE, que pone Compose por securePolicy.
        val context = LocalContext.current
        SetUpDialogWindow { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(themeDimAmount(context))
            window.keepReleasedFocusOutOfFields()
        }
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
                modifier = Modifier
                    // Solo la barra de estado: la de navegación la aparta V2SheetCard
                    // dentro de su propio fondo (mockup P20·b), y con el teclado
                    // abierto imePadding ya cubre esa franja.
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .clickable(
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

/**
 * Ajusta la ventana propia del diálogo, una sola vez por ventana: cada cambio de
 * atributos obliga al WindowManager a volver a medirla, y en una hoja con campo eso
 * ocurriría en cada tecla (gama baja, RNF-07). Si el padre no es una ventana de
 * diálogo no hace nada.
 */
@Composable
private fun SetUpDialogWindow(setUp: (Window) -> Unit) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(dialogWindow) { dialogWindow?.let(setUp) }
}

/**
 * Intensidad del atenuado que da el tema a los diálogos, la misma que la hoja tenía
 * antes de ocupar la pantalla entera: sin ella, `FLAG_DIM_BEHIND` usa 1.0 y el fondo
 * queda negro pleno.
 */
private fun themeDimAmount(context: Context): Float {
    val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.backgroundDimAmount))
    return try {
        attributes.getFloat(0, DEFAULT_DIM_AMOUNT)
    } finally {
        attributes.recycle()
    }
}

private const val DEFAULT_DIM_AMOUNT = 0.6f
