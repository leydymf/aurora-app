package com.auroraclima.app.core.ui.v2

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * Apoyo común de las pruebas del teclado. La Activity de prueba se configura como
 * MainActivity (edge-to-edge + `adjustResize`): sin eso las pruebas medirían otra
 * ventana, una que sí se achica sola, y pasarían sin probar nada.
 */
typealias ImeComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

const val IME_TIMEOUT_MS = 10_000L

fun ImeComposeRule.configureLikeMainActivity() {
    runOnUiThread {
        activity.enableEdgeToEdge()
        @Suppress("DEPRECATION")
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
