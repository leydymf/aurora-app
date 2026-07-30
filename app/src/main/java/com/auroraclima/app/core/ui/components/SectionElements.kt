package com.auroraclima.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/** Etiqueta mono en mayúsculas (manual de marca §04: 11–13 px, tracking 0.16em). */
@Composable
fun SectionTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = PlexMono,
        fontSize = 12.sp,
        letterSpacing = 0.16.em,
        color = SteelDeep
    )
}

/** Titular serif del espacio interno (Marcellus, mockups P04–P21). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = Marcellus,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        color = Night
    )
}

/**
 * Punto de viñeta + texto (señales de P11, reglas de la portada y
 * recomendaciones del nivel). Extraído de content/ y questionnaire/ que lo
 * duplicaban (P-42a); cada pantalla conserva su cuerpo tipográfico por parámetro.
 */
@Composable
fun BulletRow(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp, lineHeight: TextUnit = 22.sp) {
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Steel)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontFamily = PlexSans,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = Night
        )
    }
}
