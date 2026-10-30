package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.SessionGuard
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Seguridad en Aurora» (v0.3.4) dentro de un grafo seguro real con Ajustes: la fila
 * abre la pantalla, el gesto atrás vuelve a Ajustes, la nube sale con un toque y, con
 * letra 2.0 en una pantalla pequeña, cada fila se alcanza desplazando y la última
 * línea termina por encima de la nube.
 */
@RunWith(AndroidJUnit4::class)
class SafetyNotesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private fun text(id: Int) = context.getString(id)
    private var navController: NavHostController? = null

    private val rows = listOf(
        R.string.access_notes_cloud_title to R.string.access_notes_cloud_body,
        R.string.access_notes_enter_title to R.string.access_notes_enter_body,
        R.string.access_notes_wrong_title to R.string.access_notes_wrong_body,
        R.string.access_notes_idle_title to R.string.access_notes_idle_body,
        R.string.access_notes_captures_title to R.string.access_notes_captures_body,
        R.string.access_notes_recognize_title to R.string.access_notes_recognize_body,
        R.string.access_notes_device_title to R.string.access_notes_device_body,
        R.string.access_notes_account_title to R.string.access_notes_account_body,
        R.string.access_notes_files_title to R.string.access_notes_files_body,
        R.string.access_notes_delete_title to R.string.access_notes_delete_body
    )

    private fun currentRoute(): String? {
        var route: String? = null
        composeRule.runOnUiThread { route = navController?.currentDestination?.route }
        return route
    }

    /** Fachada → grafo seguro con Ajustes y la pantalla nueva, con su nube y su guardián. */
    private fun showSettings(fontScale: Float = 1f, small: Boolean = false) {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = if (small) Modifier.requiredSize(320.dp, 568.dp) else Modifier) {
                    val controller = rememberNavController()
                    navController = controller
                    AuroraV2Theme(V2Voice.Interior) {
                        NavHost(navController = controller, startDestination = "home") {
                            composable("home") { Text("clima") }
                            secureGraph(route = "inner", startDestination = "settings") {
                                screen("settings") {
                                    SettingsScreen(
                                        onBack = {},
                                        onChangeCode = {},
                                        onChangePassword = {},
                                        onReplayWalkthrough = {},
                                        onOpenSafetyNotes = { controller.navigate("notes") },
                                        onOpenPrivacy = {},
                                        onSignOut = {},
                                        onDeleteAccount = {}
                                    )
                                }
                                screen("notes") { SafetyNotesScreen(onBack = { controller.popBackStack() }) }
                            }
                        }
                    }
                    SessionGuard(
                        navController = controller,
                        sessionManager = session.sessionManager,
                        secureGraphRoute = "inner",
                        facadeRoute = "home"
                    )
                }
            }
        }
        composeRule.runOnUiThread {
            session.sessionManager.startSession()
            navController?.navigate("inner")
        }
        composeRule.waitForIdle()
    }

    private fun openNotes() {
        composeRule.onNode(hasText(text(R.string.access_notes_title))).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("notes", currentRoute())
    }

    @Test
    fun laFilaAbreLaPantallaYElGestoAtrasVuelveAAjustes() {
        showSettings()
        assertEquals("Seguridad en Aurora", text(R.string.access_notes_title))
        openNotes()
        composeRule.onNodeWithText(text(R.string.access_notes_tag)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.access_notes_cloud_title)).assertIsDisplayed()

        composeRule.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals("settings", currentRoute())
        composeRule.onNodeWithText(text(R.string.access_settings_title)).assertIsDisplayed()
    }

    @Test
    fun unToqueALaNubeSaleAlClima() {
        showSettings()
        openNotes()

        composeRule.onNodeWithContentDescription(text(R.string.access_back_to_weather)).performClick()
        composeRule.waitForIdle()

        assertFalse("un toque a la nube cierra la sesión", session.sessionManager.isSessionActive.value)
        assertEquals("home", currentRoute())
    }

    @Test
    fun conLetraGrandeTodoSeAlcanzaYLaUltimaLineaQuedaSobreLaNube() {
        showSettings(fontScale = 2f, small = true)
        openNotes()

        rows.forEach { (title, body) ->
            composeRule.onNodeWithText(text(title)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(text(body)).performScrollTo().assertIsDisplayed()
        }
        // Al final del desplazamiento, la última línea no queda bajo la nube.
        repeat(6) { composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        val more = composeRule.onNodeWithText(text(R.string.access_notes_more)).assertIsDisplayed()
        val cloud = composeRule.onNodeWithContentDescription(text(R.string.access_back_to_weather))
            .fetchSemanticsNode().boundsInWindow
        val line = more.fetchSemanticsNode().boundsInWindow
        assertFalse("la nube ($cloud) tapa la última línea ($line)", line.overlaps(cloud))
    }
}
