package com.auroraclima.app.core.ui.v2

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Apoyo común de las pruebas del teclado. La Activity de prueba se configura como
 * MainActivity: edge-to-edge aquí, `adjustResize` en `src/debug/AndroidManifest.xml`
 * (fijarlo en tiempo de ejecución no llegaba a la ventana en API 26) y el foco
 * soltado fuera de los campos. Sin eso las pruebas medirían otra ventana y pasarían
 * sin probar nada.
 */
typealias ImeComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

const val IME_TIMEOUT_MS = 10_000L

/**
 * Además fuerza el modo táctil. Los toques que simula Compose no pasan por el sistema
 * de entrada y no lo restauran, así que una tecla física previa —un `adb shell input
 * keyevent`, por ejemplo— deja el emulador sin modo táctil. Ahí Android devuelve el
 * foco al primer campo en cuanto la vista lo suelta, el teclado se reabre y las
 * pruebas fallan sin que la app tenga la culpa (observado en la corrida de v0.3.2).
 * Una usuaria que toca la pantalla está siempre en modo táctil.
 */
fun ImeComposeRule.configureLikeMainActivity() {
    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    runOnUiThread {
        activity.enableEdgeToEdge()
        activity.window.keepReleasedFocusOutOfFields()
    }
}

private fun ImeComposeRule.rootInsets(): WindowInsetsCompat? {
    var insets: WindowInsetsCompat? = null
    runOnUiThread { insets = ViewCompat.getRootWindowInsets(activity.window.decorView) }
    return insets
}

fun ImeComposeRule.isImeVisible(): Boolean = rootInsets()?.isVisible(WindowInsetsCompat.Type.ime()) == true

/** Alto del teclado en píxeles (0 si está cerrado). */
fun ImeComposeRule.imeBottomPx(): Int = rootInsets()?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

/** Alto de la ventana en píxeles: con edge-to-edge, el contenido la ocupa entera. */
fun ImeComposeRule.windowHeightPx(): Int {
    var height = 0
    runOnUiThread { height = activity.window.decorView.height }
    return height
}

/**
 * Baja de [tag] en píxeles de PANTALLA (sirve también para ventanas de diálogo).
 * Tras abrirse el teclado, el diseño tarda unos cuadros en ajustarse —en API 26 más
 * que en 30—: por eso [scrollIntoViewAbove] reintenta hasta que el nodo queda
 * por encima del límite, como haría una usuaria que vuelve a deslizar.
 */
private fun ImeComposeRule.screenBottomOf(tag: String): Float {
    val node = onNodeWithTag(tag).fetchSemanticsNode()
    return node.positionOnScreen.y + node.size.height
}

/**
 * Desplaza hasta [tag] y espera a que termine por encima de [limitPx]. Un píxel de
 * tolerancia: el paso de dp a píxeles redondea mitades (52 dp a 420 dpi = 136,5 px).
 */
fun ImeComposeRule.scrollIntoViewAbove(tag: String, limitPx: () -> Int) = awaitLayout(
    message = { "«$tag» termina en ${screenBottomOf(tag)} px y el límite es ${limitPx()} px" }
) {
    onNodeWithTag(tag).performScrollTo()
    waitForIdle()
    screenBottomOf(tag) <= limitPx() + PIXEL_ROUNDING
}

/**
 * Espera a que el diseño cumpla [condition] tras abrirse el teclado. Solo atrapa el
 * vencimiento del plazo: cualquier otro fallo (un nodo que no existe) sale tal cual.
 */
fun ImeComposeRule.awaitLayout(message: () -> String, condition: () -> Boolean) {
    try {
        waitUntil(IME_TIMEOUT_MS, condition)
    } catch (e: ComposeTimeoutException) {
        throw AssertionError(message(), e)
    }
}

const val PIXEL_ROUNDING = 1

/**
 * Espera a que el teclado se abra o se cierre. Si nunca aparece, la prueba FALLA
 * con la causa probable: saltarla en silencio la volvería un guardián que no guarda.
 */
fun ImeComposeRule.awaitIme(visible: Boolean) {
    try {
        waitUntil(IME_TIMEOUT_MS) { isImeVisible() == visible }
    } catch (e: ComposeTimeoutException) {
        val expected = if (visible) "abrirse" else "cerrarse"
        throw AssertionError(
            "El teclado no llegó a $expected en ${IME_TIMEOUT_MS / 1000} s. Si el emulador tiene " +
                "teclado físico (hw.keyboard=yes), activar " +
                "`adb shell settings put secure show_ime_with_hard_keyboard 1`.",
            e
        )
    }
}
