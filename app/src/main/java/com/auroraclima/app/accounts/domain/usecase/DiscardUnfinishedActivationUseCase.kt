package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.AccessCodePresence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Descarta lo que dejó un registro que no llegó a crear el código de acceso
 * (hallazgo A de la evaluación v0.3.2). Sin esto, la sesión del proveedor y el
 * blob de credenciales sobrevivían a la interrupción y el login del flujo de
 * activación los aceptaba sin contraseña: quien tuviera el teléfono y el código
 * de activación —que se comparte en las sesiones— llegaba a crear un código y
 * al espacio interno con los datos de esa cuenta.
 *
 * Corre al abrir el consentimiento (la única entrada del flujo de activación),
 * antes de crear una cuenta y al abrir el login de activación. Retomar el
 * registro sigue siendo posible, pero solo volviendo a escribir la contraseña.
 *
 * Guarda: con el código de acceso ya creado no hay activación a medias y no se
 * toca nada; ante la duda (lectura fallida) tampoco, porque borrar la sesión o
 * el blob de una usuaria activa la expulsaría de su reingreso (P-31 C).
 *
 * Singleton con mutex: el consentimiento lo lanza sin esperarlo y el login o la
 * creación de cuenta lo vuelven a pedir antes de hablar con el proveedor; el
 * mutex garantiza que ningún descarte tardío corra DESPUÉS de una sesión nueva.
 */
@Singleton
class DiscardUnfinishedActivationUseCase @Inject constructor(
    private val accessCode: AccessCodePresence,
    private val repository: AuthRepository,
    private val credentials: CredentialStore
) {
    private val running = Mutex()

    suspend operator fun invoke() {
        running.withLock {
            if (accessCode.isSet().getOrDefault(true)) return
            // Mismo orden que el cierre explícito (RF-19): primero el blob, para
            // que ningún reingreso silencioso pueda reconstruir la sesión.
            credentials.erase()
            repository.signOut()
        }
    }
}
