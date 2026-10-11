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
 * **Cubre TODOS los campos del kit, enmascaren o no.** La primera versión cubría
 * solo los que no enmascaran, con la premisa de que los de contraseña «ya estaban
 * cubiertos por `KeyboardType.Password`, que hace las dos cosas de un golpe». Esa
 * frase estuvo aquí escrita y era falsa; se midió el bytecode de Compose 1.7.8:
 *
 *  - de las dos cosas, `KeyboardType.Password` solo hace una, la del teclado, y esa
 *    sí sin condición;
 *  - la de copiar y cortar la hacía `PasswordVisualTransformation`, y **solo
 *    mientras está activa**: `TextFieldSelectionManager.showSelectionToolbar`
 *    decide con un `instanceof` de esa clase. [V2SecretField] la cambia a `None` al
 *    pulsar el ojo, así que con el ojo abierto el menú ofrecía «copiar» en la clave
 *    personal y en las contraseñas (hallazgo H-9);
 *  - y nunca cubrió `Ctrl+C`: `TextFieldKeyInput` llama a `copy()` sin consultar
 *    nada de la máscara.
 *
 * De ahí la regla de ahora, que es una y no tiene excepciones: **todo campo de
 * texto del kit va envuelto**. Si alguien cree que un campo enmascarado es
 * redundante aquí, esto es lo que tiene que leer antes de quitarlo.
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
 *    llama a `copy()` sin consultar la máscara. O sea que en los campos enmascarados
 *    **nunca** estuvo bloqueado, ni con el ojo cerrado. En el teléfono del piloto no
 *    aplica.
 *  - el **menú contextual por clic derecho**. `CoreTextField` envuelve el campo en
 *    `ContextMenuArea`, que en 1.7.8 **ya está cableado en Android** —no es un
 *    no-op— y dibuja su propio menú sin consultar [LocalTextToolbar], con el
 *    «Copiar» colgando del mismo `!isPassword` que H-9 falsificó. Lo dispara solo el
 *    botón secundario (`awaitFirstRightClickDown`), así que hace falta un ratón: con
 *    el dedo es inalcanzable. En el teléfono del piloto no aplica, pero el envoltorio
 *    NO lo cubre y conviene no creer lo contrario.
 *  - el AUTOLLENADO, que es una vía distinta del portapapeles y del diccionario del
 *    IME sobre este mismo campo. Hoy está inerte —no hay `AutofillNode` registrado
 *    ni `contentType` en el código, medido sobre el DEX— pero el andamiaje de
 *    Compose ya viaja en el APK. NO se desactiva: `importantForAutofill` solo existe
 *    a nivel de ventana, así que en la raíz dejaría sin autollenado los campos de
 *    contraseña de la cuenta y rompería los gestores de contraseñas, que es una
 *    pérdida de seguridad real; y Compose 1.7 no expone un equivalente por campo.
 *    Se revisa al subir a 1.8 (P-97).
 *
 * **Acoplado a Compose 1.7.** [InterceptPlatformTextInput] existe desde ahí, y en
 * 1.8 `TextToolbar.showMenu` cambia de firma y el menú contextual —que en 1.7.8 ya
 * viaja cableado, aunque solo lo alcance un clic derecho— pasa a ser el camino
 * principal. Es decir: en 1.8 la vía que hoy este envoltorio NO cubre deja de
 * necesitar un ratón. Cualquier subida de Compose obliga a repetir la comprobación
 * manual del menú (§10.1) y a reconsiderar esa vía: esto no lo puede verificar una
 * prueba.
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
 * Envoltorio único para los campos de texto del espacio interno. Aplica las dos
 * protecciones a la vez, para que no se pueda poner una y olvidar la otra.
 *
 * Toda pantalla interna con un campo editable debe usar un campo del kit de
 * `core/ui/v2`, y **todo** campo del kit debe estar envuelto aquí, enmascare o no
 * (H-9: enmascarar no exime, porque el ojo apaga la máscara). Lo fija
 * `SensitiveFieldsGuardTest`.
 */
@Composable
fun SecureTextEntry(content: @Composable () -> Unit) {
    val toolbar = LocalTextToolbar.current
    val noCopy = remember(toolbar) { NoCopyTextToolbar(toolbar) }
    CompositionLocalProvider(LocalTextToolbar provides noCopy) {
        NoImeLearning(content)
    }
}
