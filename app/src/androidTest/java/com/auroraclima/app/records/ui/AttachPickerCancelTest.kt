package com.auroraclima.app.records.ui

import android.app.Instrumentation
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.core.security.SecureBackStackGuard
import com.auroraclima.app.core.security.SessionGuard
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.security.rememberSystemLauncher
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cancelar el selector del sistema (v0.3.2, H-5): con el selector REAL —Photo Picker
 * o el de documentos— abierto sobre la app, volver sin elegir nada deja a la persona
 * en el editor, con lo escrito intacto y la sesión viva.
 *
 * Réplica mínima de la app con sus guardianes reales: fachada → grafo seguro con un
 * campo de texto y los lanzadores del editor (`rememberSystemLauncher`, que arma la
 * ida-y-vuelta), SecureBackStackGuard y SessionGuard. El «atrás» lo da el sistema
 * (`input keyevent`), como lo haría la persona, sobre la pantalla externa.
 */
@RunWith(AndroidJUnit4::class)
class AttachPickerCancelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private var navController: NavHostController? = null
    private var written = ""
    private var picked: String? = "sin-resultado"

    @Test
    fun cancelarElSelectorDeDocumentosVuelveAlEditorSinPerderLoEscrito() = cancelFrom("Archivo")

    @Test
    fun cancelarElSelectorDeImagenesVuelveAlEditorSinPerderLoEscrito() = cancelFrom("Galería")

    @Test
    fun cancelarElSelectorDeDocumentosConElGestoDeAtras() {
        // El gesto de atrás llegó con Android 10. Antes, deslizar desde el borde
        // abre el cajón «Open from» del selector de documentos y no vuelve: ahí
        // la vía es el botón de atrás, que cubren las otras dos pruebas.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        cancelFrom("Archivo", gesture = true)
    }

    private fun cancelFrom(button: String, gesture: Boolean = false) {
        setHarness()
        composeRule.onNodeWithText("Entrar").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("hola")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(button).performClick()
        waitUntilActivity(Lifecycle.State.CREATED)
        // El selector del sistema ya está al frente: se le da tiempo de pintar.
        Thread.sleep(SETTLE_MS)
        val back = if (gesture) EDGE_SWIPE_BACK else "input keyevent 4"
        instrumentation.uiAutomation.executeShellCommand(back).close()
        waitUntilActivity(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        var route: String? = null
        composeRule.runOnUiThread { route = navController?.currentDestination?.route }
        assertEquals("la persona sigue dentro del editor", "editor", route)
        assertEquals("lo escrito sigue ahí", "hola", written)
        assertEquals("cancelar no produce un archivo", null, picked)
        assertTrue("la sesión sigue viva", session.sessionManager.isSessionActive.value)
    }

    private fun setHarness() {
        session.setContent(composeRule) {
            val controller = rememberNavController()
            navController = controller
            NavHost(navController = controller, startDestination = "home") {
                composable("home") {
                    Button(onClick = { controller.navigate("inner") }) { Text("Entrar") }
                }
                secureGraph(route = "inner", startDestination = "editor") {
                    screen("editor") { EditorStub() }
                }
            }
            SecureBackStackGuard(
                navController = controller,
                secureGraphRoute = "inner",
                facadeRoute = "home",
                suppressStopPop = session.sessionManager::isAwaitingSystemReturn
            )
            SessionGuard(
                navController = controller,
                sessionManager = session.sessionManager,
                secureGraphRoute = "inner",
                facadeRoute = "home"
            )
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun EditorStub() {
        var text by remember { mutableStateOf("") }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            picked = uri?.toString()
        }
        val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            picked = uri?.toString()
        }
        val launchExternal = rememberSystemLauncher(onLaunchFailed = {})
        Column {
            BasicTextField(value = text, onValueChange = {
                text = it
                written = it
            })
            Button(onClick = {
                launchExternal {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }) { Text("Galería") }
            Button(onClick = {
                launchExternal {
                    pickDocument.launch(AttachmentPolicy.allowedDocumentTypes.toTypedArray())
                }
            }) { Text("Archivo") }
        }
    }

    private fun waitUntilActivity(target: Lifecycle.State) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = composeRule.activity.lifecycle.currentState
            val reached = if (target == Lifecycle.State.RESUMED) state == target else state < Lifecycle.State.STARTED
            if (reached) return
            Thread.sleep(POLL_MS)
        }
        error("La actividad no llegó a $target; estado: ${composeRule.activity.lifecycle.currentState}")
    }

    private companion object {
        const val WAIT_MS = 15_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 2_000L

        // Gesto de atrás de la navegación por gestos: desliza desde el borde izquierdo.
        const val EDGE_SWIPE_BACK = "input swipe 2 1200 600 1200 150"
    }
}
