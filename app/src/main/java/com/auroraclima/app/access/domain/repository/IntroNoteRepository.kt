package com.auroraclima.app.access.domain.repository

/**
 * Aviso de bienvenida de quien saltó el tutorial del primer uso. Solo existe
 * «pendiente» o «no»: se deja pendiente al saltar y se consume al mostrarse,
 * así que aparece una sola vez y nunca a quien vio el tutorial. Lo que se
 * guarda va cifrado y muere con la cuenta (RF-20).
 */
interface IntroNoteRepository {
    /** Deja el aviso pendiente. Mejor esfuerzo: si falla, el aviso simplemente no aparece. */
    suspend fun markPending(): Result<Unit>

    /**
     * True si había un aviso pendiente, y lo consume en el mismo paso: la
     * siguiente llamada devuelve false aunque esta se interrumpa. Lo que no se
     * puede abrir (llave ausente, dato adulterado) se descarta y cuenta como no pendiente.
     */
    suspend fun consumePending(): Result<Boolean>
}
