package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.ChangePasswordResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.UpdatePasswordResult
import com.auroraclima.app.core.security.FakeSyncKeyManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cambio de contraseña conociendo la actual (RF-39): re-autentica, re-envuelve
 * la DEK (sin re-cifrar registros), cambia la contraseña y concilia el sobre
 * con la contraseña que quedó activa. Cada fallo corta en el orden diseñado.
 */
class ChangePasswordUseCaseTest {
    private val repository = FakeAuthRepository()
    private val syncKeys = FakeSyncKeyManager()
    private val credentials = FakeCredentialStore()
    private val useCase = ChangePasswordUseCase(repository, syncKeys, credentials)

    @Test
    fun `el camino feliz re-envuelve, cambia y adopta con la contrasena nueva`() = runTest {
        val result = useCase("clave-actual-1", "clave-nueva-22")

        assertEquals(ChangePasswordResult.Success, result)
        // Re-envoltura ANTES del cambio real; adopción después, con la nueva.
        assertEquals(listOf("clave-nueva-22"), syncKeys.rewrappedPasswords)
        assertEquals(listOf("clave-nueva-22"), repository.updatedPasswords)
        assertEquals(listOf("clave-nueva-22"), syncKeys.establishedPasswords)
        assertEquals(1, repository.reauthenticateCalls)
    }

    @Test
    fun `una contrasena nueva corta se rechaza sin tocar la red`() = runTest {
        val result = useCase("clave-actual-1", "corta7c")

        assertEquals(ChangePasswordResult.WeakPassword, result)
        assertEquals(0, repository.reauthenticateCalls)
        assertTrue(syncKeys.rewrappedPasswords.isEmpty())
    }

    @Test
    fun `una credencial rechazada corta sin tocar el sobre ni la contrasena`() = runTest {
        repository.nextReauthenticateResult = ReauthenticateResult.InvalidCredentials

        val result = useCase("clave-equivocada", "clave-nueva-22")

        assertEquals(ChangePasswordResult.InvalidCredentials, result)
        assertTrue(syncKeys.rewrappedPasswords.isEmpty())
        assertTrue(repository.updatedPasswords.isEmpty())
    }

    @Test
    fun `sin conexion nada se modifica y la UI puede afirmarlo`() = runTest {
        repository.nextReauthenticateResult = ReauthenticateResult.NoConnection

        val result = useCase("clave-actual-1", "clave-nueva-22")

        assertEquals(ChangePasswordResult.NoConnection, result)
        assertTrue(syncKeys.rewrappedPasswords.isEmpty())
        assertTrue(repository.updatedPasswords.isEmpty())
    }

    @Test
    fun `si publicar el sobre falla el cambio se aborta con todo intacto`() = runTest {
        // El sobre nuevo va ANTES que updatePassword: si el servidor no
        // confirma, la contraseña del proveedor no se toca.
        syncKeys.rewrapOutcome = Result.failure(IllegalStateException())

        val result = useCase("clave-actual-1", "clave-nueva-22")

        assertEquals(ChangePasswordResult.GenericFailure, result)
        assertTrue(repository.updatedPasswords.isEmpty())
    }

    @Test
    fun `si updatePassword falla se auto-repara el sobre con la contrasena vigente`() = runTest {
        // El sobre publicado quedó adelantado (contraseña que nunca entró en
        // vigor): la conciliación corre con la ACTUAL — establish la detecta y
        // re-publica (auto-reparación probada en DefaultSyncKeyManagerTest).
        repository.nextUpdatePasswordResult = UpdatePasswordResult.GenericFailure

        val result = useCase("clave-actual-1", "clave-nueva-22")

        assertEquals(ChangePasswordResult.GenericFailure, result)
        assertEquals(listOf("clave-nueva-22"), syncKeys.rewrappedPasswords)
        assertEquals(listOf("clave-actual-1"), syncKeys.establishedPasswords)
    }

    @Test
    fun `el cambio exitoso re-escribe el blob con la contrasena nueva`() = runTest {
        // P-31 opción C, caso 2 del ciclo de vida del blob.
        credentials.save("usuaria@correo.co", "clave-actual-1")

        useCase("clave-actual-1", "clave-nueva-22")

        assertEquals("usuaria@correo.co", credentials.stored?.email)
        assertEquals("clave-nueva-22", credentials.stored?.password)
    }

    @Test
    fun `un cambio fallido conserva el blob vigente`() = runTest {
        credentials.save("usuaria@correo.co", "clave-actual-1")
        repository.nextUpdatePasswordResult = UpdatePasswordResult.GenericFailure

        useCase("clave-actual-1", "clave-nueva-22")

        assertEquals("clave-actual-1", credentials.stored?.password)
    }

    @Test
    fun `sin blob previo el cambio exitoso no crea uno`() = runTest {
        // Sin correo guardado no hay con qué re-escribir: el siguiente login
        // manual lo crea. El desenlace visible no cambia.
        assertEquals(ChangePasswordResult.Success, useCase("clave-actual-1", "clave-nueva-22"))
        assertEquals(null, credentials.stored)
    }

    @Test
    fun `un fallo de la conciliacion no cambia el desenlace visible`() = runTest {
        // Mejor esfuerzo: el siguiente login concilia (la vía rápida exige la
        // revisión al día, ningún estado intermedio pasa inadvertido).
        syncKeys.establishOutcome = Result.failure(IllegalStateException())

        assertEquals(ChangePasswordResult.Success, useCase("clave-actual-1", "clave-nueva-22"))
    }
}
