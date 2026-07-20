package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.DeleteAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.PendingErasureFlag
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Eliminación total de la cuenta y los datos (RF-20; derecho de supresión,
 * Ley 1581 de 2012). El ORDEN es la decisión de seguridad central de la sesión
 * (aprobada 2026-07-20) y es innegociable:
 *
 * 1. Re-autenticación con la contraseña — confirma a la usuaria (criterio 1),
 *    refresca el login reciente que el proveedor exige para borrar la cuenta y
 *    verifica la red ANTES de tocar nada: sin conexión no se borra nada y la UI
 *    puede afirmarlo con honestidad.
 * 2. Purga remota confirmada por el servidor. Antes que la cuenta, porque las
 *    reglas del backend exigen la sesión del uid para borrar sus documentos.
 * 3. Cuenta del proveedor (criterio 3: esas credenciales no vuelven a entrar).
 * 4. Datos locales, al final y a mejor esfuerzo: lo local es la herramienta con
 *    la que se borró lo remoto — destruirlo antes dejaría blobs y una cuenta
 *    viva imborrables. Un fallo en 1–3 detiene todo con lo local intacto y
 *    reintentar es idempotente; llegado el paso 4 ya no hay marcha atrás y cada
 *    eraser corre aunque otro falle (cripto-borrado primero, dentro de cada
 *    dueño). Nada de este flujo se registra en logs (regla 4).
 *
 * Contra la muerte del proceso (que NonCancellable no cubre): la marca durable
 * [PendingErasureFlag] se iza antes del paso 3 y solo se retira con el paso 4
 * completo; [com.auroraclima.app.core.security.PendingErasureRecovery] la lee al
 * arranque y termina el borrado local (auditoría H-1/H-3).
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteErasers: Set<@JvmSuppressWildcards RemoteAccountDataEraser>,
    private val localErasers: Set<@JvmSuppressWildcards LocalAccountDataEraser>,
    private val pendingErasure: PendingErasureFlag
) {
    suspend operator fun invoke(password: String): DeleteAccountResult {
        when (authRepository.reauthenticate(password)) {
            ReauthenticateResult.Success -> Unit
            ReauthenticateResult.InvalidCredentials -> return DeleteAccountResult.InvalidCredentials
            ReauthenticateResult.NoConnection -> return DeleteAccountResult.NoConnection
            ReauthenticateResult.GenericFailure -> return DeleteAccountResult.GenericFailure
        }
        remoteErasers.forEach { eraser ->
            eraser.erase().getOrElse { return DeleteAccountResult.GenericFailure }
        }
        // Marca durable ANTES del punto de no retorno (auditoría H-1): si el
        // proceso muriera entre borrar la cuenta y terminar lo local, el próximo
        // arranque (PendingErasureRecovery) completa el borrado. Sin la marca
        // garantizada en disco se aborta aquí — todavía no se ha perdido nada.
        if (!pendingErasure.raise()) return DeleteAccountResult.GenericFailure
        // Punto de no retorno: si la cancelación del scope (pop de la pantalla,
        // salida rápida) interrumpiera entre borrar la cuenta y borrar lo local,
        // quedaría una cuenta muerta con los datos locales intactos y sin sesión
        // para repararlo. Hasta aquí cancelar era seguro (reintento idempotente).
        return withContext(NonCancellable) {
            val deleteResult = authRepository.deleteCurrentUser()
            if (deleteResult != DeleteUserResult.Success) {
                // La cuenta sigue viva: la marca debe retirarse o el próximo
                // arranque borraría lo local de una cuenta existente.
                pendingErasure.clear()
                return@withContext when (deleteResult) {
                    DeleteUserResult.NoConnection -> DeleteAccountResult.NoConnection
                    else -> DeleteAccountResult.GenericFailure
                }
            }
            val allErased = localErasers.fold(true) { allSoFar, eraser ->
                // La cuenta ya no existe: no queda nada que reintentar contra la
                // nube, así que el fallo de un eraser no detiene a los demás.
                runCatching { eraser.erase() }.isSuccess && allSoFar
            }
            // La marca se retira solo si TODO lo local se borró; si algo falló,
            // queda izada y el próximo arranque reintenta los erasers (H-3,
            // idempotentes). Para la usuaria igual es Éxito: la cuenta y la nube
            // ya no existen y el cripto-borrado dejó ilegible cualquier resto.
            if (allErased) pendingErasure.clear()
            DeleteAccountResult.Success
        }
    }
}
