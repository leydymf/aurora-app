package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas de esquina suave del V2, cada una con función fija; ninguna pantalla
 * define radios propios (regla «tokens primero» de PLAN_UI_V2.md).
 */
object V2Corner {
    /** Tarjetas y hojas: radio 28 (anexo v2-29). */
    val card = RoundedCornerShape(28.dp)

    /** Hoja inferior (P20·b): el mismo radio 28, solo en las esquinas superiores. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Controles: botones y campos de texto. */
    val control = RoundedCornerShape(14.dp)

    /** Chips, segmentos de progreso e insignias alargadas: píldora completa. */
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Las mismas formas expuestas como Shapes de MaterialTheme (vía [AuroraV2Theme]),
 * para los componentes de Material que leen su forma del tema.
 */
val V2Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = V2Corner.control,
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = V2Corner.card
)
