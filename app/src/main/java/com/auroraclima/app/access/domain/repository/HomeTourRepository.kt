package com.auroraclima.app.access.domain.repository

/**
 * Recorrido guiado por Inicio de la primera llegada tras la activación. Solo existe
 * «pendiente» o «no»: se deja pendiente al crear el código de acceso (ahí termina la
 * activación) y se consume al mostrarse, así que aparece por sí solo una sola vez,
 * haya terminado o saltado el tutorial. Lo que se guarda va cifrado y muere con la
 * cuenta (RF-20).
 */
interface HomeTourRepository {
    /** Deja el recorrido pendiente. Mejor esfuerzo: si falla, el recorrido simplemente no aparece. */
    suspend fun markPending(): Result<Unit>

    /**
     * True si había un recorrido pendiente, y lo consume en el mismo paso: la
     * siguiente llamada devuelve false aunque esta se interrumpa. Lo que no se
     * puede abrir (llave ausente, dato adulterado) se descarta y cuenta como no pendiente.
     */
    suspend fun consumePending(): Result<Boolean>
}
