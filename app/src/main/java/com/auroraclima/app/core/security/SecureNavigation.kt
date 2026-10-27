package com.auroraclima.app.core.security

import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.auroraclima.app.core.ui.v2.CloudReturnMargin
import com.auroraclima.app.core.ui.v2.CloudReturnVisual

/**
 * Acceso al [SessionManager] desde las pantallas del grafo seguro. Lo provee la
 * navegación raíz con la instancia inyectada por Hilt (no es un singleton manual).
 */
val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    // Sin nombres propios a propósito: las cadenas de los mensajes de excepción
    // sobreviven a R8 y quedan legibles en el DEX del release (H-1, P-89).
    error("falta un proveedor requerido")
}

/**
 * Grafo de navegación seguro: el ÚNICO punto donde se aplica FLAG_SECURE (RNF-02).
 * Toda pantalla interna se declara con [SecureGraphScope.screen] dentro de este grafo
 * y queda envuelta automáticamente: no hay forma de registrar una ruta interna sin la
 * bandera, ni de aplicarla pantalla por pantalla a mano ([SecureScreen] es privado).
 *
 * Por la misma construcción, toda pantalla interna recibe el botón nube de salida
 * rápida (RF-16 criterio 1, decisión F2 de A2-6) y reporta la interacción táctil al
 * temporizador de inactividad (RNF-03). Las transiciones son [EnterTransition.None]/
 * [ExitTransition.None]: entrar y salir es instantáneo, «sin animaciones» (F2), lo
 * que garantiza el retorno en mucho menos de 1 segundo (RF-16 criterio 2).
 */
fun NavGraphBuilder.secureGraph(route: String, startDestination: String, builder: SecureGraphScope.() -> Unit) {
    navigation(
        startDestination = startDestination,
        route = route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        SecureGraphScope(this).builder()
    }
}

/**
 * Ámbito de declaración de rutas dentro de [secureGraph].
 *
 * [cloudBottomInset] desplaza SOLO el anclaje de la nube de salida rápida: la ruta
 * cuyo contenido termina en una barra de pestañas (v2-10) lo declara con la altura
 * de esa barra para que la nube flote por encima y ningún destino táctil quede
 * debajo. Es apariencia/posición — el botón, su gesto y `closeNow` no cambian
 * (RF-16); el resto de rutas conserva el anclaje actual con el valor por defecto.
 */
class SecureGraphScope internal constructor(private val graph: NavGraphBuilder) {
    fun screen(route: String, cloudBottomInset: Dp = 0.dp, content: @Composable (NavBackStackEntry) -> Unit) {
        graph.composable(route) { entry ->
            SecureScreen(cloudBottomInset = cloudBottomInset) { content(entry) }
        }
    }
}

/**
 * Mientras al menos una instancia esté en composición, la ventana bloquea capturas
 * de pantalla y oculta su contenido en la vista de recientes. Superpone además la
 * nube de salida rápida ([QuickExitCloud]) y registra la interacción
 * ([trackSessionInteraction]). Privado a propósito: las pantallas nunca lo invocan
 * directamente, solo [secureGraph].
 */
@Composable
private fun SecureScreen(cloudBottomInset: Dp, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.let(SecureFlagCounter::acquire)
        onDispose { window?.let(SecureFlagCounter::release) }
    }
    val cloudAnchor = remember { CloudAnchor() }
    CompositionLocalProvider(LocalCloudAnchor provides cloudAnchor) {
        // La nube va DESPUÉS del contenido: queda por encima de todo lo que la pantalla
        // dibuje, también de una capa que oscurezca el resto (recorrido de Inicio), y
        // el primer toque siempre le llega a ella (RF-16).
        Box(modifier = Modifier.fillMaxSize().trackSessionInteraction()) {
            content()
            QuickExitCloud(
                modifier = Modifier.align(Alignment.BottomEnd),
                bottomInset = cloudBottomInset,
                onPlaced = { cloudAnchor.bounds = it }
            )
        }
    }
}

/**
 * Dónde quedó la nube de la pantalla segura en curso, en coordenadas de la ventana;
 * null hasta su primera colocación. Es solo geometría: la leen las señales que la
 * apuntan (la práctica del tutorial y el recorrido de Inicio) para dibujar un halo o
 * dejarla iluminada, y `reserveCloudLane` para que Inicio y la portada de la Guía le
 * dejen su franja. El toque, el anclaje y `closeNow` no cambian por ella. Las nubes de
 * las ventanas propias (`SecureWindow`) no la escriben: viven en otra ventana.
 */
@Stable
class CloudAnchor internal constructor() {
    var bounds: Rect? by mutableStateOf(null)
        internal set
}

/** El [CloudAnchor] de la pantalla segura en curso; null fuera del grafo seguro y dentro de una ventana propia. */
val LocalCloudAnchor = staticCompositionLocalOf<CloudAnchor?> { null }

/**
 * La nube de salida rápida (RF-16) con su anclaje y su gesto, los mismos en la ruta
 * segura y en cada ventana propia (`SecureWindow`): una sola rutina de cierre,
 * `closeNow`. La apariencia y sus medidas (CloudReturnSize, CloudReturnMargin,
 * compartidas con keyboardAware) vienen de core/ui. El inset del teclado la mantiene
 * visible y pulsable con el teclado abierto: con edge-to-edge, sin él quedaba debajo
 * (v0.3.2). [bottomInset] la eleva sobre una barra de pestañas (ver [SecureGraphScope]).
 * [onPlaced] recibe el círculo (no la franja con sus márgenes) cada vez que se mueve.
 */
@Composable
internal fun QuickExitCloud(modifier: Modifier, bottomInset: Dp = 0.dp, onPlaced: ((Rect) -> Unit)? = null) {
    val sessionManager = LocalSessionManager.current
    CloudReturnVisual(
        onClick = sessionManager::closeNow,
        modifier = modifier
            .systemBarsPadding()
            .imePadding()
            .padding(bottom = bottomInset)
            .padding(CloudReturnMargin)
            .then(if (onPlaced == null) Modifier else Modifier.onGloballyPositioned { onPlaced(it.boundsInWindow()) })
    )
}

/**
 * Observa los toques (en la pasada Initial, sin consumirlos) y el desplazamiento para
 * reiniciar la inactividad (RNF-03). Lo usan la ruta segura y cada ventana propia: un
 * toque dentro de una hoja o un diálogo también es uso, aunque la ventana de la
 * Activity no lo reciba.
 *
 * El desplazamiento también cuenta como interacción (P-21): una lectura larga con
 * scroll no debe cerrar la sesión a los 5 minutos. El toque que inicia cada gesto ya
 * lo captura pointerInput, pero el scroll por accesibilidad (TalkBack) no genera
 * eventos de puntero; la conexión de nested scroll cubre ambos casos, acotada por
 * InteractionThrottle para no reiniciar el temporizador en cada frame de un
 * desplazamiento continuo.
 *
 * `composed`, como `keyboardAware`: la sesión se lee de [LocalSessionManager], que solo
 * existe en composición.
 */
internal fun Modifier.trackSessionInteraction(): Modifier = composed {
    val sessionManager = LocalSessionManager.current
    remember(sessionManager) {
        val throttle = InteractionThrottle(MonotonicClock(SystemClock::elapsedRealtime))
        val scrollConnection = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed != Offset.Zero && throttle.shouldRegister()) {
                    sessionManager.registerInteraction()
                }
                return Offset.Zero
            }
        }
        Modifier
            .nestedScroll(scrollConnection)
            .pointerInput(sessionManager) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            sessionManager.registerInteraction()
                        }
                    }
                }
            }
    }
}

/**
 * Aplica el conteo de [SecureFlagLedger] sobre el Window real. `object` manual por
 * excepción registrada en la guía de arquitectura (2026-07-08): es estado del Window,
 * no lógica de negocio.
 */
private object SecureFlagCounter {
    private val ledger = SecureFlagLedger()

    fun acquire(window: Window) {
        ledger.acquire()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        ledger.release()
        if (!ledger.isSecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
