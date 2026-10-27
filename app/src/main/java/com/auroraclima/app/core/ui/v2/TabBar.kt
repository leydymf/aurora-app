package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/** Una pestaña de [V2TabBar]: icono de contorno + etiqueta corta. */
data class V2TabItem(@DrawableRes val iconRes: Int, val label: String)

/**
 * Altura nominal de [V2TabBar] SIN el inset de la barra de navegación del sistema
 * (ese lo agrega el propio componente). La usa la navegación segura para elevar la
 * nube de salida rápida por encima de la barra (mockup v2-10) sin medirla.
 */
val V2TabBarHeight = 68.dp

/**
 * Barra de pestañas del sistema V2 (mockups v2-01/03 en la fachada; v2-10 y
 * siguientes en el espacio interno). Blanca, con la píldora [V2Palette.tabIndicator]
 * detrás del icono activo; la etiqueta acompaña siempre al icono (regla 3) y la
 * tipografía sale del tema, así que cada voz conserva la suya. [itemModifier] agrega
 * algo a una pestaña concreta (el recorrido por Inicio marca así las que ilumina).
 */
@Composable
fun V2TabBar(
    items: List<V2TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemModifier: (Int) -> Modifier = { Modifier }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(V2Palette.cardSurface)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(itemModifier(index))
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(index) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(30.dp)
                        .clip(V2Corner.pill)
                        .background(if (selected) V2Palette.tabIndicator else V2Palette.cardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        tint = if (selected) V2Palette.inkPrimary else V2Palette.inkSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                TabLabel(
                    text = item.label,
                    color = if (selected) V2Palette.inkPrimary else V2Palette.inkSecondary
                )
            }
        }
    }
}

/**
 * La etiqueta de una pestaña, siempre en una línea (v0.3.3). Con letra grande en una
 * pantalla angosta, «Mi registro» se partía en dos, la barra crecía y la pestaña
 * quedaba debajo de la nube de salida rápida, que flota a una altura fija sobre la
 * barra (`V2TabBarHeight`) y no se mueve. Ahora la etiqueta conserva el tamaño de
 * letra elegido si cabe, y si no, se reduce solo lo necesario para el ancho de su
 * pestaña ([firstFittingScale], comprobando cada candidato); nunca se corta ni se
 * esconde (regla 3).
 */
@Composable
private fun TabLabel(text: String, color: Color) {
    val base = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    // Un poco de aire a los lados: una etiqueta ajustada no toca la de al lado.
    BoxWithConstraints(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        val available = constraints.maxWidth
        // El medidor cambia con la densidad, la escala de letra y la dirección.
        val style = remember(text, base, available, measurer) {
            fun widthAt(style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            // El ancho no escala en proporción a la letra: cada candidato se comprueba
            // midiendo (con la cuenta proporcional sola, «Mi registro» se recortaba en
            // 320 dp con letra 2.0, `InnerTabBarLabelTest`).
            val scale = firstFittingScale(oneLineScale(widthAt(base), available)) { candidate ->
                widthAt(base.scaledBy(candidate)) <= available
            }
            base.scaledBy(scale)
        }
        Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun V2TabBarPreview() {
    AuroraV2Theme(V2Voice.Facade) {
        V2TabBar(
            items = listOf(
                V2TabItem(R.drawable.ic_weather_cloud, "Hoy"),
                V2TabItem(R.drawable.ic_weather_calendar, "5 días")
            ),
            selectedIndex = 0,
            onSelect = {}
        )
    }
}
