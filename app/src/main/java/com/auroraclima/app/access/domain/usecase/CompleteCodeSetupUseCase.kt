package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import javax.inject.Inject

/**
 * Cierre de la activación (RF-08, v0.3.3): crea el código de acceso y, solo si se
 * guardó, deja pendiente la guía de Aurora desde su primera pantalla (v0.3.4). Con el
 * código creado termina la activación (ver `DiscardUnfinishedActivationUseCase`), así
 * que la guía sale la primera vez que se llega al panel.
 *
 * Es un caso de uso propio y no una línea más de [CreateSecretCodeUseCase] porque el
 * cambio de código de Ajustes (`ChangeSecretCodeUseCase`, RF-09) delega en aquel:
 * cada cambio volvería a dejar pendiente la guía. La marca es de mejor esfuerzo:
 * si no se puede guardar, la guía simplemente no aparece y el resultado no cambia.
 */
class CompleteCodeSetupUseCase @Inject constructor(
    private val createSecretCode: CreateSecretCodeUseCase,
    private val walkthrough: WalkthroughRepository
) {
    /** Las reglas visibles en vivo, las mismas de la creación (criterio 2 de RF-08). */
    suspend fun check(code: String): CodeChecks = createSecretCode.check(code)

    suspend operator fun invoke(code: String): CreateSecretCodeResult {
        val result = createSecretCode(code)
        if (result == CreateSecretCodeResult.Created) {
            walkthrough.mark(WalkthroughMark.FROM_START)
        }
        return result
    }
}
