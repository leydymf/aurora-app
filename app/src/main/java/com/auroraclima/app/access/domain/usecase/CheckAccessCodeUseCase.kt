package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.core.security.AccessCodePresence
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Responde a [AccessCodePresence] desde access: el código existe si hay hash en
 * [CodeSlot.USER] — el mismo criterio con que [ValidateSecretCodeUseCase] decide
 * entre activación y acceso, para que ambos lados vean el mismo estado.
 */
class CheckAccessCodeUseCase @Inject constructor(
    private val repository: SecretCodeRepository
) : AccessCodePresence {
    override suspend fun isSet(): Result<Boolean> =
        runCatching { repository.storedCode(CodeSlot.USER) != null }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
}
