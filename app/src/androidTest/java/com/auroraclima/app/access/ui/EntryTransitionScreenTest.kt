package com.auroraclima.app.access.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La transición de entrada muestra la marca ~700 ms y avisa una sola vez que
 * terminó; con la preferencia de reducir animaciones la marca está quieta y
 * completa desde el primer cuadro.
 */
@RunWith(AndroidJUnit4::class)
class EntryTransitionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val brandName: String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)

    private var finished = 0

    private fun show(reduceMotion: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                EntryTransitionScreen(
                    plan = EntryTransitionPlan.forMotion(reduceMotion),
                    onFinished = { finished++ }
                )
            }
        }
    }

    @Test
    fun conAnimacionesMuestraLaMarcaYTerminaALos700ms() {
        show(reduceMotion = false)

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithText(brandName).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals("aún no se cumplen los 700 ms", 0, finished)

        composeRule.mainClock.advanceTimeBy(400)
        assertEquals(1, finished)
    }

    @Test
    fun conReduccionDeAnimacionesLaMarcaEstaQuietaYTerminaALos700ms() {
        show(reduceMotion = true)

        // Desde el primer cuadro y hasta el final, sin fundidos.
        composeRule.onNodeWithText(brandName).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithText(brandName).assertIsDisplayed()
        assertEquals(0, finished)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, finished)
    }

    @Test
    fun avisaUnaSolaVez() {
        show(reduceMotion = true)

        composeRule.mainClock.advanceTimeBy(5_000)

        assertEquals(1, finished)
    }
}
