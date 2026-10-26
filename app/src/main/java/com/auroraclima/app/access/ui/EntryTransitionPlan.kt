package com.auroraclima.app.access.ui

/**
 * Calendario de la transición de entrada: la marca aparece, se sostiene y se
 * desvanece sobre el fondo interior, en unos 700 ms en total. Es puro (sin
 * Android ni reloj) para poder probar el fundido en la JVM: la pantalla solo
 * le pregunta la opacidad que corresponde a cada instante.
 *
 * Con la preferencia de reducir animaciones, [forMotion] devuelve un calendario
 * sin fundidos: la marca se muestra quieta, completa, el mismo tiempo.
 */
internal data class EntryTransitionPlan(
    val fadeInMillis: Long,
    val holdMillis: Long,
    val fadeOutMillis: Long
) {
    val totalMillis: Long get() = fadeInMillis + holdMillis + fadeOutMillis

    /** Sin fundidos: nada se mueve, la marca está siempre a opacidad completa. */
    val isStatic: Boolean get() = fadeInMillis == 0L && fadeOutMillis == 0L

    /** Opacidad de la marca a los [elapsedMillis] de iniciada la transición, entre 0 y 1. */
    fun brandAlphaAt(elapsedMillis: Long): Float {
        val fadeOutStart = fadeInMillis + holdMillis
        return when {
            elapsedMillis < fadeInMillis -> elapsedMillis.toFloat() / fadeInMillis
            elapsedMillis < fadeOutStart -> 1f
            elapsedMillis < totalMillis -> 1f - (elapsedMillis - fadeOutStart).toFloat() / fadeOutMillis
            else -> if (fadeOutMillis == 0L) 1f else 0f
        }.coerceIn(0f, 1f)
    }

    companion object {
        const val TOTAL_MILLIS = 700L
        private const val FADE_MILLIS = 150L

        fun forMotion(reduceMotion: Boolean): EntryTransitionPlan = if (reduceMotion) {
            EntryTransitionPlan(fadeInMillis = 0L, holdMillis = TOTAL_MILLIS, fadeOutMillis = 0L)
        } else {
            EntryTransitionPlan(
                fadeInMillis = FADE_MILLIS,
                holdMillis = TOTAL_MILLIS - 2 * FADE_MILLIS,
                fadeOutMillis = FADE_MILLIS
            )
        }
    }
}
