package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
 * tipografía sale del tema, así que cada voz conserva la suya.
 */
@Composable
fun V2TabBar(items: List<V2TabItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
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
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) V2Palette.inkPrimary else V2Palette.inkSecondary
                )
            }
        }
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
