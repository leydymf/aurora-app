package com.auroraclima.app.core.security

/**
 * Documento de llaves del respaldo en la nube (RF-39): el sobre de la DEK
 * envuelto por la contraseña ([WrappedKeyEnvelope]) más su generación. Interfaz
 * en core/security para probar la máquina de estados en JVM; la implementación
 * real (Firestore, `/users/{uid}`) vive en `core/network`, único conocedor del
 * SDK (mismo patrón que las fuentes remotas de los features).
 */
interface KeyEnvelopeRemoteSource {
    /**
     * Sobre publicado de la cuenta vigente, o null si nunca se ha publicado.
     * Consulta AL SERVIDOR: decidir sobre una copia vieja de caché podría
     * re-publicar un sobre atrasado (la auto-reparación depende de ver el
     * estado real). Lanza sin red — quien llama lo traduce a fallo silencioso.
     */
    suspend fun fetch(): PublishedKeyEnvelope?

    /**
     * Publica (crea o reemplaza) el sobre esperando la confirmación del
     * servidor: hasta entonces el estado local no debe avanzar — un encolado
     * offline dejaría dos dispositivos decidiendo sobre generaciones distintas.
     */
    suspend fun publish(envelope: PublishedKeyEnvelope)

    /** Elimina el documento de llaves (RF-20: nada de la cuenta queda en la nube). */
    suspend fun delete()
}

/**
 * Lo único que viaja al documento de llaves (RNF-01/RNF-04): el sobre (material
 * indescifrable sin la contraseña), sus parámetros públicos de derivación, la
 * generación, la revisión y la versión del formato.
 *
 * [generation] identifica LA DEK (solo cambia cuando el respaldo se reinicia
 * con llave nueva — es lo que dispara la re-subida de registros); [revision]
 * identifica ESTA publicación del sobre (cambia en cada publish, también al
 * re-envolver con otra contraseña la misma DEK). La vía rápida sin KDF exige
 * ambas al día: así un cambio de contraseña interrumpido a mitad nunca pasa
 * inadvertido (ver ADR RF-39).
 */
data class PublishedKeyEnvelope(
    val envelope: WrappedKeyEnvelope,
    val generation: Long,
    val revision: String,
    val schemaVersion: Long = CURRENT_SCHEMA_VERSION
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1L
    }
}
