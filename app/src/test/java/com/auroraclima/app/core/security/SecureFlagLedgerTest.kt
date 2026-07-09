package com.auroraclima.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RNF-02: la bandera de pantalla segura debe permanecer encendida mientras exista
 * al menos una pantalla del espacio interno visible, incluidas las transiciones de
 * navegación en las que dos pantallas seguras conviven en composición.
 */
class SecureFlagLedgerTest {
    private val ledger = SecureFlagLedger()

    @Test
    fun `sin pantallas seguras la bandera esta apagada`() {
        assertFalse(ledger.isSecure)
    }

    @Test
    fun `una pantalla segura enciende la bandera`() {
        ledger.acquire()
        assertTrue(ledger.isSecure)
    }

    @Test
    fun `la transicion entre dos pantallas seguras no apaga la bandera`() {
        ledger.acquire() // pantalla saliente
        ledger.acquire() // pantalla entrante (conviven durante la transición)
        ledger.release() // la saliente se descarta
        assertTrue(ledger.isSecure)
    }

    @Test
    fun `al salir la ultima pantalla segura la bandera se apaga`() {
        ledger.acquire()
        ledger.acquire()
        ledger.release()
        ledger.release()
        assertFalse(ledger.isSecure)
    }

    @Test
    fun `un release de mas no deja el conteo negativo`() {
        ledger.release() // desbalance accidental
        ledger.acquire()
        assertTrue(ledger.isSecure)
    }
}
