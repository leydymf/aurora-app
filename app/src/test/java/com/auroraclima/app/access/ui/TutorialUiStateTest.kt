package com.auroraclima.app.access.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reapertura del tutorial (RF-11): en modo repaso «Atrás» existe desde la
 * primera página — es la vía de regreso a configuración —, mientras que en el
 * primer uso (RF-10) la primera página no lo ofrece porque detrás no hay nada.
 */
class TutorialUiStateTest {
    @Test
    fun `primer uso - la primera pagina no ofrece atras`() {
        val state = TutorialUiState(step = TutorialStep.ENTER)
        assertFalse(state.showsBack(isReview = false))
    }

    @Test
    fun `repaso - la primera pagina ofrece atras para regresar`() {
        val state = TutorialUiState(step = TutorialStep.ENTER)
        assertTrue(state.showsBack(isReview = true))
    }

    @Test
    fun `paginas posteriores ofrecen atras en ambos modos`() {
        for (step in listOf(TutorialStep.CLOSE_NOW, TutorialStep.AUTO_CLOSE)) {
            val state = TutorialUiState(step = step)
            assertTrue(state.showsBack(isReview = false))
            assertTrue(state.showsBack(isReview = true))
        }
    }

    @Test
    fun `primer uso - ofrece saltar por ahora`() {
        // Refinamiento RF-10 (2026-08): el tutorial es saltable en primer uso.
        val state = TutorialUiState(step = TutorialStep.ENTER)
        assertTrue(state.showsSkip(isReview = false))
    }

    @Test
    fun `repaso - no ofrece saltar`() {
        // Regresión RF-11: en repaso la vía de regreso sigue siendo «Atrás»;
        // «Saltar» no aparece ni en la primera página ni en las posteriores.
        for (step in listOf(TutorialStep.ENTER, TutorialStep.CLOSE_NOW, TutorialStep.AUTO_CLOSE)) {
            val state = TutorialUiState(step = step)
            assertFalse(state.showsSkip(isReview = true))
        }
    }
}
