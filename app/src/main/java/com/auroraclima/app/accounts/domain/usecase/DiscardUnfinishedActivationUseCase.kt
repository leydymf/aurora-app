package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.AccessCodePresence
import com.auroraclima.app.core.security.CloudReconciliation
import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Descarta lo que dejó un registro que no llegó a crear el código de acceso
 * (hallazgo A de la evaluación v0.3.2). Sin esto, la sesión del proveedor y el
 * blob de credenciales sobrevivían a la interrupción y el login del flujo de
 * activación los aceptaba sin contraseña: quien tuviera el teléfono y el código
 * de activación —que se comparte en las sesiones— llegaba a crear un código y
 * al espacio interno con los datos de esa cuenta.
 *
 * Hallazgo A′: el login de activación restaura los registros de la nube ANTES
 * de que exista el código (RF-39), y el almacén local no distingue cuentas;
 * otra cuenta creada encima los vería y los subiría como suyos. Por eso también
 * corren los borradores locales de RF-20, salvo los que se conservan
 * ([LocalAccountDataEraser.keptOnUnfinishedActivation]). No se pierde nada de
 * la usuaria: sin código de acceso no hay pantalla que cree registros, así que
 * lo local es solo lo restaurado, que la nube repone al volver a escribir la
 * contraseña (lo vigila `ActivationReachabilityGuardTest`).
 *
 * Corre al abrir la bienvenida (la única entrada del flujo de activación),
 * antes de crear una cuenta y al abrir el login de activación. Retomar el
 * registro sigue siendo posible, pero solo volviendo a escribir la contraseña.
 *
 * Guarda: con el código de acceso ya creado no hay activación a medias y no se
 * toca nada; ante la duda (lectura fallida) tampoco, porque borrar la sesión,
 * el blob o los datos de una usuaria activa sería irreparable.
 *
 * Singleton con mutex: la bienvenida lo lanza sin esperarlo y el login o la
 * creación de cuenta lo vuelven a pedir antes de hablar con el proveedor; el
 * mutex garantiza que ningún descarte tardío corra DESPUÉS de una sesión nueva.
 */
@Singleton
class DiscardUnfinishedActivationUseCase @Inject constructor(
    private val accessCode: AccessCodePresence,
    private val repository: AuthRepository,
    private val credentials: CredentialStore,
    private val cloudReconciliation: CloudReconciliation,
    private val localErasers: Set<@JvmSuppressWildcards LocalAccountDataEraser>
) {
    private val running = Mutex()

    suspend operator fun invoke() {
        running.withLock {
            if (accessCode.isSet().getOrDefault(true)) return
            // Mismo orden que el cierre explícito (RF-19): primero el blob, para
            // que ningún reingreso silencioso pueda reconstruir la sesión.
            credentials.erase()
            repository.signOut()
            // Sin cancelación a mitad: el pop de la bienvenida no debe dejar
            // la mitad de los almacenes borrada y la otra mitad viva.
            withContext(NonCancellable) {
                // Antes de borrar, que nada de la otra cuenta siga escribiendo:
                // una reconciliación viva reabriría las bases a mitad del borrado.
                cloudReconciliation.stopAndWait()
                localErasers.filterNot { it.keptOnUnfinishedActivation }.forEach { eraser ->
                    // Mejor esfuerzo por dueño, como en RF-20: un fallo no frena al resto.
                    runCatching { eraser.erase() }
                }
            }
        }
    }
}
