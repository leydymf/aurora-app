package com.auroraclima.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Latch de la miniatura de recientes (RF-14, auditoría A-1 del cierre pre-merge).
 * La API de Android no tiene getter para el flag, así que la decisión vive en una
 * función pura y es ella la que se prueba; que el composable sea su único
 * aplicador lo vigila `NoTracesGuardTest`, y el efecto visible se comprueba en la
 * corrida manual (§8-A1 de `RESULTADOS_PRUEBAS.md`).
 */
class RecentsThumbnailGuardTest {
    @Test
    fun `con texto en la barra la miniatura se oculta`() {
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.SearchChanged,
            hidden = true,
            resumed = true
        )

        assertFalse(decision!!)
    }

    @Test
    fun `al vaciar la barra en primer plano la miniatura se restaura`() {
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.SearchChanged,
            hidden = false,
            resumed = true
        )

        assertTrue(decision!!)
    }

    @Test
    fun `al vaciar la barra con la fachada en segundo plano NO se restaura`() {
        // El latch: ON_PAUSE vacía la barra antes de que se dibuje el frame sin
        // el código, así que restaurar aquí volvería a exponer el frame viejo.
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.SearchChanged,
            hidden = false,
            resumed = false
        )

        assertNull(decision)
    }

    @Test
    fun `al volver al frente sin busqueda la miniatura se restaura`() {
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.ReturnedToForeground,
            hidden = false,
            resumed = true
        )

        assertTrue(decision!!)
    }

    @Test
    fun `al volver al frente con la busqueda activa la miniatura sigue oculta`() {
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.ReturnedToForeground,
            hidden = true,
            resumed = true
        )

        assertNull(decision)
    }

    @Test
    fun `al dejar la fachada la miniatura se restaura siempre`() {
        // Dentro manda FLAG_SECURE; una app de clima sin miniatura sería un
        // distinguidor, así que la fachada nunca se queda sin ella — ni siquiera
        // saliendo con la búsqueda abierta y la actividad en segundo plano.
        val decision = recentsThumbnailDecision(
            ThumbnailTrigger.LeftFacade,
            hidden = true,
            resumed = false
        )

        assertTrue(decision!!)
    }
}
