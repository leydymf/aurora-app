package com.auroraclima.app.content.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp

/**
 * Elementos compartidos del módulo de información. Las filas y tarjetas V1
 * (ContentListRow y compañía) murieron con la migración V2 de la S3: cada
 * pantalla compone ahora las piezas de core/ui/v2. Quedan la salida al
 * marcador y la holgura de la nube.
 */

/** Espacio final para que el contenido no quede bajo el botón nube (RF-16). */
internal val CONTENT_BOTTOM_CLEARANCE = 88.dp

/** Ancho reservado para que la nube (52 dp + margen) no tape una acción fija (RF-16). */
internal val CLOUD_CLEARANCE = 68.dp

/**
 * Abre el marcador con el número puesto (RF-23, decisión F5): ACTION_DIAL no
 * exige permiso alguno (RNF-04), no marca por sí solo y la app no conserva
 * rastro. Sin marcador instalado (p. ej. tablet WiFi) la acción se descarta en
 * silencio: un crash sería una anomalía visible y quedaría en logcat (D4 S2
 * B-1). Único punto de salida al teléfono de todo el módulo.
 */
internal fun dialNumber(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
}
