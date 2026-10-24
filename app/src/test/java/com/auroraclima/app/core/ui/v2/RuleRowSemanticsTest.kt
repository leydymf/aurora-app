package com.auroraclima.app.core.ui.v2

import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// Sin ui-test en la JVM: se lee el árbol de semántica de una ComposeView dentro de una actividad de Robolectric.
@RunWith(RobolectricTestRunner::class)
class RuleRowSemanticsTest {
    private class Pantalla(private val view: ComposeView) {
        @OptIn(InternalComposeUiApi::class)
        fun raiz(): SemanticsNode {
            shadowOf(Looper.getMainLooper()).idle()
            return (view.getChildAt(0) as ViewRootForTest).semanticsOwner.rootSemanticsNode
        }

        fun regla(): SemanticsNode = raiz().children.single()

        fun descripcion(): List<String>? = regla().config.getOrNull(SemanticsProperties.ContentDescription)
    }

    private fun mostrar(content: @Composable () -> Unit): Pantalla {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply { setContent(content) }
        activity.setContentView(view, ViewGroup.LayoutParams(1080, 1920))
        return Pantalla(view)
    }

    @Test
    fun `una regla pendiente se anuncia con su texto y su estado`() {
        val pantalla = mostrar { RuleRow(text = "Mínimo 8 caracteres", satisfied = false) }

        assertEquals(listOf("Mínimo 8 caracteres, pendiente"), pantalla.descripcion())
    }

    @Test
    fun `una regla cumplida se anuncia con su texto y su estado`() {
        val pantalla = mostrar { RuleRow(text = "Mínimo 8 caracteres", satisfied = true) }

        assertEquals(listOf("Mínimo 8 caracteres, cumplida"), pantalla.descripcion())
    }

    @Test
    fun `al pasar de pendiente a cumplida la descripcion cambia y se anuncia como region viva`() {
        var cumplida by mutableStateOf(false)
        val pantalla = mostrar { RuleRow(text = "Mínimo 10 caracteres", satisfied = cumplida) }
        assertEquals(listOf("Mínimo 10 caracteres, pendiente"), pantalla.descripcion())

        cumplida = true

        assertEquals(listOf("Mínimo 10 caracteres, cumplida"), pantalla.descripcion())
        assertEquals(LiveRegionMode.Polite, pantalla.regla().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `el renglon es un solo elemento para el lector de pantalla`() {
        val pantalla = mostrar { RuleRow(text = "Mínimo 8 caracteres", satisfied = true) }

        assertEquals(0, pantalla.regla().children.size)
        assertEquals(null, pantalla.regla().config.getOrNull(SemanticsProperties.Text))
    }

    @Test
    fun `la descripcion es el texto y el estado separados por coma`() {
        assertEquals("Mínimo 8 caracteres, cumplida", ruleDescription("Mínimo 8 caracteres", "cumplida"))
    }
}
