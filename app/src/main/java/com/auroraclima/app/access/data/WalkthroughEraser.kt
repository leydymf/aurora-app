package com.auroraclima.app.access.data

import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aporte de access a la eliminación total (RF-20): la marca de la guía muere con
 * la cuenta. Tampoco se conserva al descartar una activación sin terminar
 * (valor por defecto de [keptOnUnfinishedActivation]): a esa cuenta nadie la
 * estrenó y la marca no debe heredarla la siguiente.
 */
@Singleton
class WalkthroughEraser @Inject constructor(
    private val repository: SealedWalkthroughRepository
) : LocalAccountDataEraser {
    override suspend fun erase() {
        repository.clear()
    }
}
