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
        for (step in listOf(TutorialStep.QUICK_EXIT, TutorialStep.AUTO_CLOSE)) {
            val state = TutorialUiState(step = step)
            assertTrue(state.showsBack(isReview = false))
            assertTrue(state.showsBack(isReview = true))
        }
    }
}
