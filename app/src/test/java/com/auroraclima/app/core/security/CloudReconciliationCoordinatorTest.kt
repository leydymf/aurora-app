package com.auroraclima.app.core.security

import com.auroraclima.app.core.network.ConnectivityMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Disparo de la reconciliación con la nube (ADR 2026-10-06). Lo que se fija
 * aquí es el contrato de la casa: sin red no se intenta nada —la app sigue
 * funcionando igual que siempre—, sin llave tampoco, una petición mientras otra
 * corre se descarta en vez de encolarse, y el fallo de una feature no frena a
 * las otras ni sale de aquí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudReconciliationCoordinatorTest {
    private class FakeConnectivity(private val online: Boolean) : ConnectivityMonitor {
        override val isOnline: Flow<Boolean> = flowOf(online)
    }

    private fun coordinator(reconcilers: Set<AccountDataReconciler>, online: Boolean = true, generation: Long? = 4) =
        CloudReconciliationCoordinator(
            reconcilers = reconcilers,
            keyManager = FakeSyncKeyManager(dek = ByteArray(32), generation = generation),
            connectivity = FakeConnectivity(online),
            scope = CoroutineScope(UnconfinedTestDispatcher())
        )

    @Test
    fun `con red y llave corre todos los reconciliadores`() = runTest {
        var records = 0
        var outcomes = 0

        coordinator(setOf(AccountDataReconciler { records++ }, AccountDataReconciler { outcomes++ })).reconcile()

        assertEquals(1, records)
        assertEquals(1, outcomes)
    }

    @Test
    fun `sin red no se intenta nada`() = runTest {
        var calls = 0

        coordinator(setOf(AccountDataReconciler { calls++ }), online = false).reconcile()

        assertEquals(0, calls)
    }

    @Test
    fun `sin llave establecida no se intenta nada`() = runTest {
        var calls = 0

        coordinator(setOf(AccountDataReconciler { calls++ }), generation = null).reconcile()

        assertEquals(0, calls)
    }

    @Test
    fun `el fallo de una feature no impide la otra ni sale del coordinador`() = runTest {
        var second = 0
        val failing = AccountDataReconciler { throw IllegalStateException("fallo de red del SDK") }

        coordinator(setOf(failing, AccountDataReconciler { second++ })).reconcile()

        assertEquals(1, second)
    }

    @Test
    fun `una peticion mientras otra corre se descarta en vez de repetir el trabajo`() = runTest {
        // Entrar al espacio y abrir «Mi registro» ocurren casi seguidos.
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val slow = AccountDataReconciler {
            calls++
            gate.await()
        }
        val coordinator = coordinator(setOf(slow))

        val first = launch { coordinator.reconcile() }
        advanceUntilIdle()
        coordinator.reconcile()
        gate.complete(Unit)
        first.join()

        assertEquals(1, calls)
    }

    @Test
    fun `terminada una, la siguiente si corre`() = runTest {
        var calls = 0
        val coordinator = coordinator(setOf(AccountDataReconciler { calls++ }))

        coordinator.reconcile()
        coordinator.reconcile()

        assertEquals(2, calls)
    }

    @Test
    fun `la salida rapida cancela la reconciliacion pedida`() = runTest {
        // Sin esto sería el único trabajo de fondo que sobrevive al cierre de
        // la sesión: seguiría descifrando contenido y escribiendo en la base
        // con la usuaria ya fuera (auditoría 2026-10-06 A-3).
        val gate = CompletableDeferred<Unit>()
        var finished = false
        val coordinator = coordinator(
            setOf(
                AccountDataReconciler {
                    gate.await()
                    finished = true
                }
            )
        )

        coordinator.request()
        advanceUntilIdle()
        coordinator.onSessionCleanup()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, finished)
    }
}
