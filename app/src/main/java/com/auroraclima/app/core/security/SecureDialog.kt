package com.auroraclima.app.core.security

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import com.auroraclima.app.core.ui.v2.CloudReturnClearance
import com.auroraclima.app.core.ui.v2.keepReleasedFocusOutOfFields

/**
 * Único diálogo permitido en el espacio interno (RNF-02, auditoría A1): los diálogos,
 * popups y bottom sheets crean una ventana PROPIA que no es la de la Activity, así que
 * el grafo seguro no los cubre; aquí la bandera se fuerza en la ventana del diálogo.
 * NoTracesGuardTest veda las APIs de ventana propia fuera de core/security; si una
 * pantalla interna necesita otra variante (bottom sheet, popup), el envoltorio se
 * agrega aquí con la misma política.
 *
 * Tarjeta centrada sobre [SecureWindow], que pone la bandera, el teclado y la nube de
 * salida rápida (RF-16). El contenido se desplaza: con el teclado abierto, en la
 * confirmación con contraseña de RF-20 el botón «Eliminar definitivamente» quedaba
 * cortado (corrida en el emulador). Por eso el contenido no puede llevar su propia
 * lista perezosa ni otro desplazamiento vertical (lo primero lo vigila
 * `KeyboardAwareGuardTest`). El ancho imita el de la plataforma: márgenes laterales y
 * un tope para pantallas anchas.
 */
@Composable
fun SecureDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    SecureWindow(
        onDismissRequest = onDismissRequest,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        contentAlignment = Alignment.Center,
        contentModifier = Modifier
            .systemBarsPadding()
            .imePadding()
            // La tarjeta se centra en lo que la nube deja libre: nunca debajo de ella.
            .padding(bottom = CloudReturnClearance)
            .padding(horizontal = DIALOG_SIDE_MARGIN)
            .widthIn(max = DIALOG_MAX_WIDTH)
            .fillMaxWidth(),
        content = content
    )
}

/**
 * Variante de hoja inferior de [SecureDialog] (mockup P20·b, V2): la MISMA ventana —
 * [SecureWindow], con FLAG_SECURE forzada— con el contenido anclado abajo a todo el
 * ancho. No es el ModalBottomSheet de Material: esa API crea su ventana propia sin la
 * bandera y queda vedada fuera de core/security (NoTracesGuardTest). El toque en el
 * fondo atenuado y el botón atrás descartan, como en el diálogo.
 *
 * La hoja aparta la barra de estado y el teclado (la de navegación la aparta
 * `V2SheetCard` dentro de su propio fondo, mockup P20·b); si el contenido no cabe, se
 * desplaza, y por eso no puede llevar su propia lista perezosa (lo vigila
 * `KeyboardAwareGuardTest`). `V2SheetCard` termina con la franja de la nube.
 */
@Composable
fun SecureSheet(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    SecureWindow(
        onDismissRequest = onDismissRequest,
        contentAlignment = Alignment.BottomCenter,
        contentModifier = Modifier
            .statusBarsPadding()
            .imePadding(),
        content = content
    )
}

/**
 * La ventana propia de TODA hoja, diálogo o selector del espacio interno. Reúne en un
 * solo sitio lo que la Activity recibe del grafo seguro y una ventana aparte no:
 *
 *  - FLAG_SECURE (`SecureOn`, RNF-02);
 *  - la NUBE de salida rápida (RF-16, v0.3.2): la ventana propia cubre la de la
 *    Activity, y antes la salida rápida pedía un toque para cerrar la hoja y otro
 *    para la nube. Es la misma [QuickExitCloud] de la ruta segura, en la misma
 *    esquina y con el mismo `closeNow`: un solo toque, con la hoja o el diálogo
 *    abierto y con el teclado abierto. El pop a la fachada saca la pantalla de
 *    composición y la ventana se cierra con ella;
 *  - el registro de interacción (RNF-03): los toques dentro de la ventana no llegan a
 *    la de la Activity, y sin él una hoja abierta dejaba correr la inactividad;
 *  - pantalla completa (`decorFitsSystemWindows = false`): cada variante aparta ella
 *    misma barras y teclado, porque la plataforma no achicaba estas ventanas (API 30
 *    la hoja, API 26 el diálogo). Esa ventana perdía el fondo atenuado, que se pide
 *    explícito (`FLAG_DIM_BEHIND` con la intensidad del tema);
 *  - el foco soltado que no vuelve al campo en API 26–27 (`KeyboardAware.kt`).
 *
 * El fondo atenuado descarta si [dismissOnClickOutside]; el contenido consume su
 * toque. [scrollable] es falso solo para contenido de alto fijo con listas propias
 * (el selector de fecha).
 */
@Composable
internal fun SecureWindow(
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment,
    // No es el modificador de la raíz (la raíz es la ventana entera): va al contenido.
    @Suppress("ModifierParameter") contentModifier: Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = secureWindowProperties(dismissOnBackPress, dismissOnClickOutside)
    ) {
        // Solo suma FLAG_DIM_BEHIND; no toca FLAG_SECURE, que pone Compose por securePolicy.
        val context = LocalContext.current
        SetUpDialogWindow { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(themeDimAmount(context))
            window.keepReleasedFocusOutOfFields()
        }
        WindowBackdrop(
            onClick = onDismissRequest.takeIf { dismissOnClickOutside },
            contentAlignment = contentAlignment
        ) {
            Box(
                modifier = contentModifier
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    // El contenido consume el toque: tocarlo no debe descartar. Con un
                    // gesto en lugar de `clickable` el desplazamiento por semántica
                    // dejaba de llegar al último botón del diálogo (instrumentadas);
                    // cómo lo agrupa TalkBack se revisa en P-120.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                content()
            }
            QuickExitCloud(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * Toda la ventana: el fondo atenuado, que descarta con [onClick] (null si no
 * descarta), y el registro de interacción, porque los toques dentro de la ventana no
 * llegan a la de la Activity (RNF-03).
 */
@Composable
private fun WindowBackdrop(
    onClick: (() -> Unit)?,
    contentAlignment: Alignment,
    content: @Composable BoxScope.() -> Unit
) {
    val dismiss = if (onClick == null) {
        // Sin descarte no hay nodo pulsable: TalkBack no anuncia un fondo que no hace nada.
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .trackSessionInteraction()
            .then(dismiss),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Propiedades de toda ventana propia: FLAG_SECURE forzada (`SecureOn`, RNF-02) y
 * pantalla completa, porque cada variante aparta ella misma barras y teclado.
 */
private fun secureWindowProperties(dismissOnBackPress: Boolean, dismissOnClickOutside: Boolean) = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = false,
    securePolicy = SecureFlagPolicy.SecureOn,
    decorFitsSystemWindows = false
)

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
 * Intensidad del atenuado que da el tema a los diálogos, la misma que tenían antes de
 * ocupar la pantalla entera: sin ella, `FLAG_DIM_BEHIND` usa 1.0 y el fondo queda
 * negro pleno.
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

/** Márgenes y tope de ancho del diálogo, cerca de los de la plataforma en un teléfono. */
private val DIALOG_SIDE_MARGIN = 40.dp
private val DIALOG_MAX_WIDTH = 560.dp
