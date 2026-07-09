package com.auroraclima.app.core.security

import com.auroraclima.app.core.security.di.SessionTimerScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sesión del espacio interno (RF-16, RNF-03): única fuente de verdad de si el doble
 * fondo está desbloqueado y único dueño de la rutina de limpieza de estado sensible.
 *
 * Todos los caminos de cierre convergen en [closeSession] — la misma rutina para:
 * - la salida rápida ([quickExit], RF-16),
 * - los 5 minutos de inactividad (RNF-03),
 * - el paso a segundo plano prolongado ([appMovedToBackground]),
 * - y los cierres que se sumen después (p. ej. descarte del cuestionario, RF-28).
 *
 * El cierre ejecuta los [SensitiveStateCleaner] registrados y emite [closeEvents];
 * quien navega (SessionGuard) reacciona sacando el grafo seguro de la pila, lo que
 * además destruye los ViewModels internos y su estado en memoria.
 *
 * Confinado al hilo principal: sus métodos se invocan desde la UI y sus
 * temporizadores corren en [scope] (Main.immediate); no hay estado compartido
 * entre hilos.
 */
@Singleton
class SessionManager @Inject constructor(
    @SessionTimerScope private val scope: CoroutineScope,
    private val monotonicClock: MonotonicClock,
    private val boundCleaners: Set<@JvmSuppressWildcards SensitiveStateCleaner>
) {
    // Main-confined como el resto de la clase: lista simple, sin colección concurrente.
    private val dynamicCleaners = mutableListOf<SensitiveStateCleaner>()

    private val _isSessionActive = MutableStateFlow(false)

    /** `true` mientras el espacio interno está desbloqueado. */
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _closeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Un evento por cada cierre efectivo de sesión. Sin replay: un cierre emitido
     * sin colector se descarta, y esa pérdida es tolerable por diseño — SessionGuard
     * colecta desde la primera composición (antes de que pueda iniciarse sesión
     * alguna) y, aun perdido el evento, `isSessionActive` ya quedó en `false` y
     * SecureBackStackGuard limpia la pila en ON_STOP: nunca se queda nada abierto.
     */
    val closeEvents: SharedFlow<Unit> = _closeEvents.asSharedFlow()

    private var inactivityJob: Job? = null
    private var backgroundJob: Job? = null
    private var backgroundSinceMs: Long? = null

    /** Idempotente: entrar al grafo seguro con la sesión ya activa no reinicia nada. */
    fun startSession() {
        if (_isSessionActive.value) return
        _isSessionActive.value = true
        restartInactivityTimer()
    }

    /** Cada toque en una pantalla interna reinicia el plazo de inactividad (RNF-03). */
    fun registerInteraction() {
        if (!_isSessionActive.value) return
        restartInactivityTimer()
    }

    /**
     * ON_STOP sin rotación: si la app no vuelve dentro de [BACKGROUND_GRACE_MS], la
     * sesión se cierra. El back stack ya lo limpió SecureBackStackGuard en el mismo
     * ON_STOP; la gracia solo conserva el desbloqueo para que una interrupción breve
     * (llamada, cambio momentáneo de app) no exija iniciar sesión de nuevo.
     */
    fun appMovedToBackground() {
        if (!_isSessionActive.value) return
        backgroundSinceMs = monotonicClock.nowMs()
        backgroundJob?.cancel()
        backgroundJob = scope.launch {
            delay(BACKGROUND_GRACE_MS)
            closeSession()
        }
    }

    /**
     * Defensa en profundidad (auditoría H-3): el temporizador de gracia usa `delay`,
     * que se congela con el dispositivo suspendido o el proceso en caché. Al volver
     * se re-verifica con el reloj monótono — que SÍ cuenta la suspensión — y un
     * background más largo que la gracia cierra la sesión aunque el timer nunca
     * haya corrido.
     */
    fun appReturnedToForeground() {
        backgroundJob?.cancel()
        backgroundJob = null
        val since = backgroundSinceMs
        backgroundSinceMs = null
        if (!_isSessionActive.value) return
        if (since != null && monotonicClock.nowMs() - since >= BACKGROUND_GRACE_MS) {
            closeSession()
        } else {
            restartInactivityTimer()
        }
    }

    /** Salida rápida (RF-16): mismo cierre que el automático, disparado por la nube. */
    fun quickExit() {
        closeSession()
    }

    /**
     * Rutina única de limpieza. Idempotente y segura ante doble invocación: solo el
     * primer cierre ejecuta los cleaners y emite el evento.
     */
    fun closeSession() {
        if (!_isSessionActive.value) return
        _isSessionActive.value = false
        inactivityJob?.cancel()
        inactivityJob = null
        backgroundJob?.cancel()
        backgroundJob = null
        backgroundSinceMs = null
        runCleaners()
        _closeEvents.tryEmit(Unit)
    }

    /**
     * Registro dinámico para objetos de vida corta (RF-28 lo usará desde el ViewModel
     * del cuestionario). Cerrar el registro devuelto lo da de baja.
     */
    fun registerCleaner(cleaner: SensitiveStateCleaner): AutoCloseable {
        dynamicCleaners += cleaner
        return AutoCloseable { dynamicCleaners -= cleaner }
    }

    private fun restartInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            closeSession()
        }
    }

    private fun runCleaners() {
        // Un cleaner que falle no detiene a los demás; el fallo no se registra en
        // ningún log (regla 4 de CLAUDE.md).
        (boundCleaners + dynamicCleaners).forEach { cleaner ->
            runCatching { cleaner.onSessionCleanup() }
        }
    }

    companion object {
        /** 5 minutos de inactividad (RNF-03, criterio 1). */
        const val INACTIVITY_TIMEOUT_MS: Long = 5 * 60 * 1000

        /** Gracia tras pasar a segundo plano antes de cerrar la sesión. */
        const val BACKGROUND_GRACE_MS: Long = 30 * 1000
    }
}
