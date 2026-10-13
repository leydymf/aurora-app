package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.core.security.SearchGateResult
import javax.inject.Inject

/**
 * Cambia la clave personal (RF-09) componiendo los dos casos de uso de RF-08/RF-12:
 * [ValidateSecretCodeUseCase] confirma la clave vigente — misma puerta de la
 * fachada: hash con salt y comparación de tiempo constante contra [CodeSlot.USER];
 * solo [SearchGateResult.OpenAccess] autoriza, así que el código de activación no
 * puede autorizar un cambio — y [CreateSecretCodeUseCase] aplica las mismas reglas
 * de creación (criterio 2 de RF-09) y persiste el nuevo hash sobre la misma ranura:
 * la clave anterior deja de abrir por construcción.
 *
 * Decisión de la usuaria (sesión C1, 2026-07-17): la credencial exigida es la clave
 * vigente, no la contraseña de la cuenta — la contraseña ya se confirmó al entrar
 * (cada ingreso pasa por inicio de sesión, P-31/AuthSessionCleaner) y así la
 * verificación es 100 % local. Nota registrada en RF-09 (`docs/REQUERIMIENTOS.md`).
 *
 * El cambio NO recifra la base de datos: la passphrase de SQLCipher nunca se deriva
 * de la clave (ADR 2026-07-10 de `core/database`, pensado justamente para RF-09).
 */
class ChangeSecretCodeUseCase @Inject constructor(
    private val validateSecretCode: ValidateSecretCodeUseCase,
    private val createSecretCode: CreateSecretCodeUseCase
) {
    /** Estado en vivo de las reglas de RF-08 sobre la clave nueva (criterio 2). */
    suspend fun check(newCode: String): CodeChecks = createSecretCode.check(newCode)

    suspend operator fun invoke(currentCode: String, newCode: String): ChangeSecretCodeResult {
        // Un fallo interno de la puerta degrada a PassThrough y por tanto a
        // «no coincide»: nunca cruza una excepción y nunca autoriza de más (§3).
        if (validateSecretCode.inspect(currentCode) != SearchGateResult.OpenAccess) {
            return ChangeSecretCodeResult.CurrentCodeMismatch
        }
        return when (val result = createSecretCode(newCode)) {
            CreateSecretCodeResult.Created -> ChangeSecretCodeResult.Changed
            is CreateSecretCodeResult.Rejected -> ChangeSecretCodeResult.Rejected(result.checks)
            CreateSecretCodeResult.StorageFailure -> ChangeSecretCodeResult.StorageFailure
        }
    }
}

sealed interface ChangeSecretCodeResult {
    /** Nueva clave guardada; la anterior ya no abre (RF-09, nota de RF-08). */
    data object Changed : ChangeSecretCodeResult

    /** La clave vigente no coincide: el cambio no se autoriza (criterio 1). */
    data object CurrentCodeMismatch : ChangeSecretCodeResult

    /** La clave nueva no cumple las reglas de RF-08; [checks] dice cuál (criterio 2). */
    data class Rejected(val checks: CodeChecks) : ChangeSecretCodeResult

    /** Fallo interno de Keystore/almacenamiento: mensaje genérico en la UI. */
    data object StorageFailure : ChangeSecretCodeResult
}
