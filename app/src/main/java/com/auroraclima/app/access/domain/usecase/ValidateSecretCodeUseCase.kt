package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.access.domain.security.constantTimeEquals
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SearchQueryGate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decide si el texto tecleado en la barra de búsqueda es un código de acceso
 * (RF-05, RF-12). El código nunca se conserva ni se registra: se hashea con el salt
 * almacenado y se compara contra el hash guardado en tiempo constante.
 *
 * Regla: mientras no exista código de usuaria, el código de activación abre el
 * registro (RF-05); cuando la usuaria crea el suyo (RF-08), la activación queda
 * reemplazada y solo el código personal abre el acceso (RF-12).
 *
 * Cualquier fallo interno (Keystore, almacenamiento corrupto) degrada en silencio a
 * [SearchGateResult.PassThrough]: la fachada jamás muestra un error causado por la
 * puerta (RF-13; §3 de ARQUITECTURA.md — las excepciones nunca cruzan hacia la UI).
 */
class ValidateSecretCodeUseCase @Inject constructor(
    private val repository: SecretCodeRepository,
    private val hasher: SecretCodeHasher
) : SearchQueryGate {
    override suspend fun inspect(query: String): SearchGateResult =
        runCatching { evaluate(query) }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            SearchGateResult.PassThrough
        }

    private suspend fun evaluate(query: String): SearchGateResult {
        val candidate = query.trim()
        if (candidate.length < MIN_CODE_LENGTH) return SearchGateResult.PassThrough
        repository.storedCode(CodeSlot.USER)?.let { userCode ->
            return if (matches(candidate, userCode)) {
                SearchGateResult.OpenAccess
            } else {
                SearchGateResult.PassThrough
            }
        }
        val activationCode = repository.storedCode(CodeSlot.ACTIVATION)
            ?: return SearchGateResult.PassThrough
        return if (matches(candidate, activationCode)) {
            SearchGateResult.OpenActivation
        } else {
            SearchGateResult.PassThrough
        }
    }

    private fun matches(candidate: String, stored: StoredCodeHash): Boolean =
        constantTimeEquals(hasher.hash(stored.salt, candidate), stored.hash)

    companion object {
        /** Longitud mínima de un código (regla de RF-08); por debajo ni se hashea. */
        const val MIN_CODE_LENGTH = 6
    }
}
