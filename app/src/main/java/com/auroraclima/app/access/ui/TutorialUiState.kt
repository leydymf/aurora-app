package com.auroraclima.app.access.ui

/**
 * Estado del tutorial del primer uso (RF-10, mockups alta-fidelidad-11/12).
 * Tres páginas informativas y una práctica final; la práctica de salida usa la
 * nube real de la esquina (la pinta el grafo seguro), por eso aquí no hay paso
 * «terminado»: salir con la nube ES terminar el tutorial.
 */
data class TutorialUiState(
    val step: TutorialStep = TutorialStep.ENTER,
    val practiceInput: String = "",
    val practiceStatus: PracticeStatus = PracticeStatus.TYPING
) {
    val isFirstStep: Boolean get() = step == TutorialStep.ENTER

    /**
     * En primer uso la primera página no ofrece «Atrás» (detrás no hay nada: el
     * setup salió de la pila); en modo repaso (RF-11) sí, y regresa a donde
     * estaba la usuaria.
     */
    fun showsBack(isReview: Boolean): Boolean = !isFirstStep || isReview

    /**
     * «Saltar por ahora» existe solo en el primer uso (refinamiento RF-10,
     * 2026-08): en repaso (RF-11) no hay nada que saltar — «Atrás» ya regresa.
     */
    fun showsSkip(isReview: Boolean): Boolean = !isReview
}

/**
 * Páginas del tutorial, en el orden del pager (criterio 1 de RF-10).
 *
 * Los nombres de las constantes de un enum llegan LEGIBLES al DEX del release:
 * R8 los conserva porque `name` y `valueOf` son reflexión. Así que aquí el
 * vocabulario importa tanto como en `strings.xml` (RNF-05, H-8).
 */
enum class TutorialStep {
    /** Cómo entrar: la clave tecleada tal como en el clima. */
    ENTER,

    /** La nube de la esquina regresa al clima al instante (RF-16). */
    CLOSE_NOW,

    /** Cierre automático y qué ve un tercero (RNF-03). */
    AUTO_CLOSE,

    /** Práctica real de ingreso y salida (criterio 2 de RF-10). */
    PRACTICE
}

/** Resultado en vivo de la barra de práctica. */
enum class PracticeStatus {
    /** Aún no hay texto suficiente para evaluar. */
    TYPING,

    /** El texto no es la clave de la usuaria. */
    MISMATCH,

    /** Ingreso de práctica logrado: queda fijo aunque el texto cambie. */
    MATCHED
}
