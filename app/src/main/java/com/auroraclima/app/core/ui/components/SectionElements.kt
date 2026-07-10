package com.auroraclima.app.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexMono
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
