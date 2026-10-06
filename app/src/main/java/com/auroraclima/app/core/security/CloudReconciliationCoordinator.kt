package com.auroraclima.app.core.security

import com.auroraclima.app.core.network.ConnectivityMonitor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Disparo de la reconciliación con la nube. Es interfaz para que las pantallas
 * y los casos de uso que la piden no dependan del coordinador concreto y las
 * pruebas puedan sustituirla por una función vacía (§3).
 */
interface CloudReconciliation {
    /** Reconcilia y espera. Lo usan las pantallas, en su propio scope. */
    suspend fun reconcile()

    /**
     * Pide una reconciliación y NO la espera. La usa el inicio de sesión: la
     * usuaria no debe mirar un spinner mientras se recorre la nube entera, que
     * es lo que pasaba al esperarla dentro del login (auditoría 2026-10-06).
     */
    fun request()
}

/**
 * Dispara la reconciliación con la nube (ADR 2026-10-06) desde los tres puntos
 * en que la usuaria espera ver lo que hizo en su otro teléfono: al entrar al
 * espacio protegido, al abrir «Mi registro» y al abrir los resultados
 * guardados.
 *
 * Reglas de la casa que cumple por construcción:
 *
 * - **No bloquea la pantalla.** Es `suspend` y quien llama lo lanza en su
 *   propio scope; la pantalla pinta primero lo local y se refresca después.
 * - **Sin red no hace nada.** Se consulta [ConnectivityMonitor] antes de
 *   intentarlo: sin conexión la app sigue funcionando exactamente igual que
 *   hasta ahora, sin esperas ni reintentos.
 * - **Sin llave tampoco.** Sin DEK establecida no habría cómo abrir lo
 *   descargado y el intento sería ruido.
 * - **Una a la vez.** Entrar al espacio y abrir «Mi registro» ocurren casi
 *   seguidos; la segunda petición se descarta en vez de encolarse, porque
 *   repetir el trabajo recién hecho no aporta nada.
 * - **Silenciosa (§3, regla 4).** Ningún fallo sale de aquí ni se registra.
 * - **Muere con la sesión.** Es [SensitiveStateCleaner]: la salida rápida, la
 *   inactividad y el cierre de sesión cancelan lo que esté en vuelo. Sin esto
 *   sería el único trabajo de fondo exento de la rutina de cierre, y podría
 *   seguir descifrando contenido, escribiendo en la base y dejando temporales
 *   de evidencias DESPUÉS del barrido de la sesión (auditoría 2026-10-06 A-3).
 *   Las llamadas a [reconcile] desde las pantallas ya mueren con su ViewModel;
 *   la que necesitaba dueño es [request].
 */
@Singleton
class CloudReconciliationCoordinator @Inject constructor(
    private val reconcilers: Set<@JvmSuppressWildcards AccountDataReconciler>,
    private val keyManager: SyncKeyManager,
    private val connectivity: ConnectivityMonitor,
    @CloudReconciliationScope private val scope: CoroutineScope
) : CloudReconciliation, SensitiveStateCleaner {
    private val running = Mutex()

    // `request()` se llama desde la corrutina del login y `onSessionCleanup()`
    // desde el hilo principal: sin @Volatile, el cierre podría leer una
    // referencia vieja y no cancelar nada (auditoría 2026-10-06 H-3).
    @Volatile
    private var requested: Job? = null

    override fun request() {
        requested?.cancel()
        requested = scope.launch { reconcile() }
    }

    override fun onSessionCleanup() {
        requested?.cancel()
        requested = null
    }

    override suspend fun reconcile() {
        // tryLock, no withLock: si ya hay una corriendo, esta sobra.
        if (!running.tryLock()) return
        try {
            if (!isReady()) return
            reconcilers.forEach { reconciler ->
                try {
                    reconciler.reconcile()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (ignored: Exception) {
                    // Mejor esfuerzo por feature: el fallo de una no frena a las otras.
                }
            }
        } finally {
            running.unlock()
        }
    }

    private suspend fun isReady(): Boolean = try {
        connectivity.isOnline.first() && keyManager.currentGeneration() != null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unavailable: Exception) {
        // currentGeneration lanza si la copia local no descifra: hoy no se puede.
        false
    }
}
