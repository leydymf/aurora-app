package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.repository.HomeTourRepository
import javax.inject.Inject

/**
 * Cierre de la activación (RF-08, v0.3.3): crea el código de acceso y, solo si se
 * guardó, deja pendiente el recorrido por Inicio. Con el código creado termina la
 * activación (ver `DiscardUnfinishedActivationUseCase`), así que el recorrido sale la
 * primera vez que se llega al panel, se termine o se salte el tutorial.
 *
 * Es un caso de uso propio y no una línea más de [CreateSecretCodeUseCase] porque el
 * cambio de código de Ajustes (`ChangeSecretCodeUseCase`, RF-09) delega en aquel:
 * cada cambio volvería a dejar pendiente el recorrido. La marca es de mejor esfuerzo:
 * si no se puede guardar, el recorrido simplemente no aparece y el resultado no cambia.
 */
class CompleteCodeSetupUseCase @Inject constructor(
    private val createSecretCode: CreateSecretCodeUseCase,
    private val homeTour: HomeTourRepository
) {
    /** Las reglas visibles en vivo, las mismas de la creación (criterio 2 de RF-08). */
    suspend fun check(code: String): CodeChecks = createSecretCode.check(code)

    suspend operator fun invoke(code: String): CreateSecretCodeResult {
        val result = createSecretCode(code)
        if (result == CreateSecretCodeResult.Created) {
            homeTour.markPending()
        }
        return result
    }
}
