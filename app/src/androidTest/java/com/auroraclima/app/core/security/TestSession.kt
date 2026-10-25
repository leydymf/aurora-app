package com.auroraclima.app.core.security

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.auroraclima.app.core.ui.v2.ImeComposeRule
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource

/**
 * Un [SessionManager] real y sin limpiezas para las pruebas instrumentadas, con su
 * ámbito cancelado al terminar cada prueba. Lo necesita toda prueba que abra una
 * hoja, un diálogo o el selector de fecha: su ventana dibuja la nube de salida
 * rápida (`SecureWindow`, RF-16), que lee [LocalSessionManager]. Lo que se prueba
 * con ella es la posición y el toque, no la limpieza. [timers] permite un reloj
 * virtual para el plazo de inactividad.
 */
class TestSession(timers: CoroutineContext = Dispatchers.Main) : ExternalResource() {
    private val scope = CoroutineScope(SupervisorJob() + timers)

    val sessionManager = SessionManager(
        scope = scope,
        monotonicClock = MonotonicClock(SystemClock::elapsedRealtime),
        boundCleaners = emptySet()
    )

    /** Pone [content] en la prueba con esta sesión en [LocalSessionManager]. */
    fun setContent(composeRule: ImeComposeRule, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalSessionManager provides sessionManager, content = content)
        }
    }

    override fun after() {
        scope.cancel()
    }
}
