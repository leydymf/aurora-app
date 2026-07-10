package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.CityNameDirectory
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Crea el código personal de la usuaria (RF-08): valida las reglas visibles del
 * mockup P06 y guarda únicamente hash y salt en [CodeSlot.USER] — el código en
 * claro nunca se persiste ni viaja a la nube (criterio 3, regla 5 de CLAUDE.md).
 * Desde ese momento el código de activación deja de abrir el acceso (nota de
 * RF-08; ya implementado en [ValidateSecretCodeUseCase]).
 *
 * El candidato se recorta antes de validar y hashear, con el mismo recorte que
 * aplica la puerta de búsqueda: un espacio accidental al teclear la clave no
 * puede producir un código imposible de reingresar (RF-12).
 */
class CreateSecretCodeUseCase @Inject constructor(
    private val repository: SecretCodeRepository,
    private val hasher: SecretCodeHasher,
    private val cityDirectory: CityNameDirectory
) {
    /** Estado en vivo de las reglas visibles mientras la usuaria teclea (criterio 2). */
    suspend fun check(code: String): SecretCodeChecks {
        val candidate = code.trim()
        return SecretCodeChecks(
            hasMinLength = candidate.length >= ValidateSecretCodeUseCase.MIN_CODE_LENGTH,
            isNotRealCity = candidate.isNotEmpty() && !isRealCity(candidate)
        )
    }

    suspend operator fun invoke(code: String): CreateSecretCodeResult {
        val candidate = code.trim()
        val checks = check(candidate)
        if (!checks.allSatisfied) return CreateSecretCodeResult.Rejected(checks)
        return try {
            val salt = hasher.newSalt()
            repository.saveCode(
                CodeSlot.USER,
                StoredCodeHash(salt = salt, hash = hasher.hash(salt, candidate))
            )
            CreateSecretCodeResult.Created
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // Keystore o almacenamiento fallando: error genérico, nada en logs (§3).
            CreateSecretCodeResult.StorageFailure
        }
    }

    /**
     * Si el directorio local falla, la regla degrada a «no es ciudad»: un recurso
     * corrupto no debe impedir crear la clave (la regla es una salvaguarda de uso,
     * no la barrera criptográfica; §3 — las excepciones nunca cruzan a la UI).
     */
    private suspend fun isRealCity(candidate: String): Boolean = try {
        cityDirectory.isRealCity(candidate)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        false
    }
}

/** Reglas visibles del mockup P06; cada una alimenta un renglón de la lista en vivo. */
data class SecretCodeChecks(
    val hasMinLength: Boolean,
    val isNotRealCity: Boolean
) {
    val allSatisfied: Boolean get() = hasMinLength && isNotRealCity
}

sealed interface CreateSecretCodeResult {
    /** Código guardado como hash; el flujo continúa (RF-08 criterio 1). */
    data object Created : CreateSecretCodeResult

    /** Alguna regla no se cumple; [checks] dice cuál para explicarla (criterio 2). */
    data class Rejected(val checks: SecretCodeChecks) : CreateSecretCodeResult

    /** Fallo interno de Keystore/almacenamiento: mensaje genérico en la UI. */
    data object StorageFailure : CreateSecretCodeResult
}
