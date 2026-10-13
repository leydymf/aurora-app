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
 * - la salida rápida ([closeNow], RF-16),
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
    private var backgroundGraceMs: Long = BACKGROUND_GRACE_MS
    private var systemReturnArmedAtMs: Long? = null
    private var inSystemReturn = false

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
     * Ida y vuelta esperada a una pantalla del sistema (RF-33): la usuaria va a
     * elegir o capturar un archivo con el selector o la cámara DEL SISTEMA —
     * ambas son activities externas y disparan el mismo ON_STOP que «irse de la
     * app». Armar esta excepción de un solo uso hace dos cosas en el ON_STOP
     * inmediato: SecureBackStackGuard NO limpia la pila (la usuaria debe volver
     * al formulario con su selección) y la gracia se amplía a
     * [SYSTEM_RETURN_GRACE_MS] — tomar una foto tarda más de 30 s.
     *
     * La excepción caduca en [SYSTEM_RETURN_ARM_WINDOW_MS] si la pantalla externa
     * nunca llega a abrirse, se consume con el primer background y se descarta en
     * cualquier retorno o cierre: nunca queda armada de más.
     */
    fun expectSystemReturn() {
        if (!_isSessionActive.value) return
        systemReturnArmedAtMs = monotonicClock.nowMs()
    }

    /** Descarta una excepción armada (p. ej. si el lanzamiento del selector falló). */
    fun cancelSystemReturn() {
        systemReturnArmedAtMs = null
        inSystemReturn = false
    }

    /**
     * `true` mientras una ida-y-vuelta al sistema está armada (recién pedida) o en
     * curso (background con la excepción consumida). SecureBackStackGuard lo
     * consulta en ON_STOP para conservar la pila.
     */
    fun isAwaitingSystemReturn(): Boolean {
        if (inSystemReturn) return true
        val armedAt = systemReturnArmedAtMs ?: return false
        return monotonicClock.nowMs() - armedAt < SYSTEM_RETURN_ARM_WINDOW_MS
    }

    /**
     * ON_STOP sin rotación: si la app no vuelve dentro del plazo de gracia, la
     * sesión se cierra. El back stack ya lo limpió SecureBackStackGuard en el mismo
     * ON_STOP (salvo ida-y-vuelta al sistema); la gracia solo conserva el desbloqueo
     * para que una interrupción breve (llamada, cambio momentáneo de app) no exija
     * iniciar sesión de nuevo. Con una ida-y-vuelta armada la gracia se amplía por
     * esta única vez y la excepción queda consumida.
     */
    fun appMovedToBackground() {
        if (!_isSessionActive.value) return
        inSystemReturn = isAwaitingSystemReturn()
        systemReturnArmedAtMs = null
        backgroundGraceMs = if (inSystemReturn) SYSTEM_RETURN_GRACE_MS else BACKGROUND_GRACE_MS
        backgroundSinceMs = monotonicClock.nowMs()
        backgroundJob?.cancel()
        backgroundJob = scope.launch {
            delay(backgroundGraceMs)
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
        val grace = backgroundGraceMs
        backgroundSinceMs = null
        backgroundGraceMs = BACKGROUND_GRACE_MS
        inSystemReturn = false
        systemReturnArmedAtMs = null
        if (!_isSessionActive.value) return
        if (since != null && monotonicClock.nowMs() - since >= grace) {
            closeSession()
        } else {
            restartInactivityTimer()
        }
    }

    /**
     * Salida rápida (RF-16): mismo cierre que el automático, disparado por la nube.
     *
     * El nombre es deliberadamente neutro y no debe «mejorarse». El anterior,
     * `quickExit`, sobrevivía a R8 y quedaba legible en el DEX del release —medido
     * sobre el APK—, y «quick exit» es el nombre canónico en inglés de los botones
     * de escape en las apps de este dominio, es decir el distinguidor más directo
     * que puede dejarse en un artefacto (RNF-05; hallazgo H-8 de la evaluación
     * OWASP). `closeNow` dice lo mismo y no señala nada.
     */
    fun closeNow() {
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
        backgroundGraceMs = BACKGROUND_GRACE_MS
        systemReturnArmedAtMs = null
        inSystemReturn = false
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

        /**
         * Gracia ampliada para una ida-y-vuelta esperada al sistema (RF-33):
         * elegir un archivo o tomar una foto tarda más que la gracia normal.
         * Igual al plazo de inactividad — la exposición total nunca supera el
         * presupuesto de RNF-03 (el temporizador de inactividad sigue corriendo
         * en paralelo y cierra igual a los 5 min sin interacción).
         */
        const val SYSTEM_RETURN_GRACE_MS: Long = INACTIVITY_TIMEOUT_MS

        /** Validez del armado: si la pantalla externa no abre en este lapso, caduca. */
        const val SYSTEM_RETURN_ARM_WINDOW_MS: Long = 15 * 1000
    }
}
