package com.auroraclima.app.core.security

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Protecciones de los campos de texto del espacio interno (hallazgo H-7 de la
 * evaluación OWASP, P-94). Van juntas porque cierran las dos vías por las que el
 * contenido de un campo puede salir de la app sin que la app lo envíe:
 *
 *  1. el PORTAPAPELES, si la usuaria copia a mano desde el campo, y
 *  2. el DICCIONARIO DEL TECLADO, si el IME aprende lo que se escribe y lo
 *     sugiere después en otra aplicación.
 *
 * Los campos de contraseña ya estaban cubiertos por `KeyboardType.Password`, que
 * hace las dos cosas de un golpe. El problema son los campos que la usuaria tiene
 * que LEER mientras escribe —el relato de un registro, el correo—, donde enmascarar
 * no es opción. Para esos está este archivo.
 *
 * Vive en `core/security` por la misma razón que [SecureDialog]: es una política de
 * seguridad, no un detalle visual, y conviene que esté en un solo sitio auditable.
 *
 * **Se aplica DENTRO del componente del kit, no alrededor de la pantalla.** No es
 * un detalle: un `Dialog` instala su propia [LocalTextToolbar], así que el campo de
 * correo de RF-46, que vive dentro de un `SecureSheet`, perdería la protección si el
 * envoltorio estuviera por fuera de la hoja. Quien mueva esto hacia arriba la rompe
 * en silencio.
 *
 * **Lo que NO cierra**, y está declarado como residual en el informe (§10.1) y en
 * `PENDIENTES.md`:
 *
 *  - las acciones semánticas `CopyText` y `SetSelection`, que un servicio de
 *    accesibilidad puede invocar sin pasar por el menú, y la lectura del árbol de
 *    accesibilidad, que `FLAG_SECURE` no cubre. No se tapan con
 *    `clearAndSetSemantics` a propósito: dejaría al relato —el campo más largo de
 *    la app— sin lector de pantalla, y ese precio no lo paga el modelo de amenaza.
 *  - `Ctrl+C` con un teclado físico: Compose lo atiende en `TextFieldKeyInput`, que
 *    no pasa por la barra de selección. En el teléfono del piloto no aplica.
 *
 * **Acoplado a Compose 1.7.** [InterceptPlatformTextInput] existe desde ahí, y en
 * 1.8 `TextToolbar.showMenu` cambia de firma y la barra de selección se sustituye
 * por la API de menú contextual nueva. Cualquier subida de Compose obliga a repetir
 * la comprobación manual del menú (§10.1): esto no lo puede verificar una prueba.
 */

/**
 * Barra de selección de texto SIN «copiar» ni «cortar». «Pegar» y «seleccionar
 * todo» se conservan: la usuaria necesita poder pegar en el relato, y seleccionar
 * sin poder copiar no saca nada de la app.
 *
 * Compose dibuja el menú flotante a partir de las devoluciones de llamada que
 * recibe: pasar `null` en una de ellas hace desaparecer esa opción. Por eso basta
 * con delegar en la barra real anulando dos.
 *
 * Alcance exacto: quita las dos opciones del MENÚ. Las otras vías —accesibilidad y
 * `Ctrl+C`— están declaradas arriba y en el informe como residuales.
 */
private class NoCopyTextToolbar(private val delegate: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus get() = delegate.status

    override fun hide() = delegate.hide()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = delegate.showMenu(
        rect = rect,
        onCopyRequested = null,
        onPasteRequested = onPasteRequested,
        onCutRequested = null,
        onSelectAllRequested = onSelectAllRequested
    )
}

/**
 * Añade [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] a lo que el campo le declara
 * al teclado, para que el IME no incorpore a su diccionario personal lo que se
 * escriba dentro.
 *
 * Por qué así y no con `autoCorrect = false`, que era lo primero que se intentó:
 * se midió el `EditorInfo` real con `adb shell dumpsys input_method` y
 * `autoCorrect = false` solo RETIRA `TYPE_TEXT_FLAG_AUTO_CORRECT` — no añade
 * `TYPE_TEXT_FLAG_NO_SUGGESTIONS` ni nada que hable de aprendizaje. La barra de
 * búsqueda, que lo usa, enviaba `inputType=0x1` pelado. Es decir: costaba la
 * autocorrección y no protegía. El flag que Android define para esto es el de
 * aquí, y Compose no lo expone en `KeyboardOptions`; la vía es interceptar el
 * `EditorInfo` antes de que llegue al teclado, que es lo que permite
 * [InterceptPlatformTextInput] desde Compose 1.7.
 *
 * La API es experimental y por eso se usa en un único sitio: si cambia, cambia
 * aquí. `SensitiveFieldsGuardTest` vigila que los campos sensibles sigan pasando
 * por este envoltorio.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoImeLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val noLearning = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(noLearning)
        },
        content = content
    )
}

/**
 * Envoltorio único para los campos de texto del espacio interno que NO son de
 * contraseña. Aplica las dos protecciones a la vez, para que no se pueda poner una
 * y olvidar la otra.
 *
 * Toda pantalla interna con un campo editable debe usar un campo del kit de
 * `core/ui/v2`, y todo campo del kit que no enmascare su contenido debe estar
 * envuelto aquí. Lo fija `SensitiveFieldsGuardTest`.
 */
@Composable
fun SecureTextEntry(content: @Composable () -> Unit) {
    val toolbar = LocalTextToolbar.current
    val noCopy = remember(toolbar) { NoCopyTextToolbar(toolbar) }
    CompositionLocalProvider(LocalTextToolbar provides noCopy) {
        NoImeLearning(content)
    }
}
