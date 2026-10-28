package com.auroraclima.app.access.domain.repository

/**
 * La marca de la guía de Aurora (v0.3.4): una sola ranura, sellada, con dos valores
 * posibles. Se consume al leerse, así que lo que señala ocurre una sola vez. Lo que se
 * guarda va cifrado y muere con la cuenta (RF-20) y con una activación sin terminar.
 */
interface WalkthroughRepository {
    /**
     * Deja [mark] en la ranura (reemplaza lo que hubiera). Mejor esfuerzo: si falla, lo
     * que señala simplemente no ocurre.
     */
    suspend fun mark(mark: WalkthroughMark): Result<Unit>

    /**
     * Si hay una marca guardada, sin abrirla ni consumirla. Barato: no toca la llave.
     */
    suspend fun hasMark(): Boolean

    /**
     * Si la marca ya se puede abrir. La llave es la de la cuenta y falta mientras no
     * hay sesión: justo después de la nube, hasta que termina el reingreso silencioso.
     */
    suspend fun canOpen(): Boolean

    /**
     * La marca que había, y la consume en el mismo paso: la siguiente llamada devuelve
     * null aunque esta se interrumpa. Sin llave todavía ([canOpen] en falso) no la toca
     * y devuelve null: queda para cuando se pueda abrir. Lo que no abre con la llave
     * presente (otra cuenta, dato adulterado) se descarta y cuenta como ninguna.
     */
    suspend fun consume(): Result<WalkthroughMark?>
}

/**
 * Qué queda pendiente de la guía. Los nombres llegan LEGIBLES al DEX del release (R8
 * conserva los de un enum): por eso son sosos (RNF-05, H-8).
 */
enum class WalkthroughMark {
    /** La guía completa, desde la primera pantalla: se deja al crear el código de acceso. */
    FROM_START,

    /** Solo la pantalla final: se deja antes de la salida real de la parte 3. */
    TO_FINISH
}
