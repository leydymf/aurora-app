package com.auroraclima.app.core.security

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2PlainField
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.awaitIme
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.core.ui.v2.imeBottomPx
import com.auroraclima.app.core.ui.v2.keyboardAware
import com.auroraclima.app.core.ui.v2.windowHeightPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La nube de salida rápida (RF-16 criterio 1) sigue visible y pulsable con el
 * teclado abierto. Antes de v0.3.2 se anclaba solo sobre la barra de navegación
 * y, con edge-to-edge, el teclado la tapaba entera.
 *
 * Se arma el grafo seguro real (`secureGraph`/`screen`) con un `SessionManager`
 * real y sin limpiezas: lo que se prueba es la posición y el toque, no la limpieza.
 */
@RunWith(AndroidJUnit4::class)
class SecureScreenKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionManager = SessionManager(
        scope = scope,
        monotonicClock = MonotonicClock(SystemClock::elapsedRealtime),
        boundCleaners = emptySet()
    )

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun laNubeQuedaSobreElTecladoYCierraLaSesion() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSessionManager provides sessionManager) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = GRAPH) {
                    secureGraph(route = GRAPH, startDestination = SCREEN) {
                        screen(SCREEN) {
                            var text by remember { mutableStateOf("") }
                            AuroraV2Theme(V2Voice.Interior) {
                                Column(modifier = Modifier.fillMaxSize().keyboardAware().padding(24.dp)) {
                                    V2PlainField(
                                        value = text,
                                        onValueChange = { text = it },
                                        modifier = Modifier.testTag(FIELD),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnUiThread { sessionManager.startSession() }
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.awaitIme(visible = true)
        composeRule.waitForIdle()

        val cloud = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.access_back_to_weather)
        )
        val visibleBottom = composeRule.windowHeightPx() - composeRule.imeBottomPx()
        val cloudBottom = cloud.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(
            "La nube termina en $cloudBottom px y el teclado empieza en $visibleBottom px",
            cloudBottom <= visibleBottom
        )

        cloud.performClick()
        composeRule.waitForIdle()
        assertFalse("Tocar la nube con el teclado abierto debe cerrar la sesión", sessionManager.isSessionActive.value)
    }

    private companion object {
        const val GRAPH = "g"
        const val SCREEN = "s"
        const val FIELD = "field"
    }
}
