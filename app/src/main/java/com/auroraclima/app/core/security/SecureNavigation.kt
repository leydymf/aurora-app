package com.auroraclima.app.core.security

import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.auroraclima.app.core.ui.v2.CloudReturnVisual

/**
 * Acceso al [SessionManager] desde las pantallas del grafo seguro. Lo provee la
 * navegación raíz con la instancia inyectada por Hilt (no es un singleton manual).
 */
val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    error("LocalSessionManager sin proveer: envolver la NavHost raíz")
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

/** Ámbito de declaración de rutas dentro de [secureGraph]. */
class SecureGraphScope internal constructor(private val graph: NavGraphBuilder) {
    fun screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
        graph.composable(route) { entry ->
            SecureScreen { content(entry) }
        }
    }
}

/**
 * Mientras al menos una instancia esté en composición, la ventana bloquea capturas
 * de pantalla y oculta su contenido en la vista de recientes. Superpone además la
 * nube de salida rápida y observa los toques (en la pasada Initial, sin consumirlos)
 * para reiniciar la inactividad. Privado a propósito: las pantallas nunca lo invocan
 * directamente, solo [secureGraph].
 */
@Composable
private fun SecureScreen(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.let(SecureFlagCounter::acquire)
        onDispose { window?.let(SecureFlagCounter::release) }
    }
    val sessionManager = LocalSessionManager.current
    // El desplazamiento también cuenta como interacción (RNF-03, P-21): una lectura
    // larga con scroll no debe cerrar la sesión a los 5 minutos. El toque que inicia
    // cada gesto ya lo captura pointerInput, pero el scroll por accesibilidad
    // (TalkBack) no genera eventos de puntero; esta conexión de nested scroll cubre
    // ambos casos, acotada por InteractionThrottle para no reiniciar el temporizador
    // en cada frame de un desplazamiento continuo.
    val scrollInteractionConnection = remember(sessionManager) {
        val throttle = InteractionThrottle(MonotonicClock(SystemClock::elapsedRealtime))
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed != Offset.Zero && throttle.shouldRegister()) {
                    sessionManager.registerInteraction()
                }
                return Offset.Zero
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollInteractionConnection)
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
    ) {
        content()
        // Salida rápida (RF-16): la posición al alcance del pulgar y el gesto son
        // del mecanismo; la apariencia (nube violeta V2) se inyecta desde core/ui.
        CloudReturnVisual(
            onClick = sessionManager::quickExit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(QUICK_EXIT_MARGIN)
        )
    }
}

private val QUICK_EXIT_MARGIN = 20.dp

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
