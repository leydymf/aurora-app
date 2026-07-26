package com.auroraclima.app.core.security

/**
 * Almacén de la copia local de la DEK del respaldo (RF-39, «sobre doble»).
 * Es una interfaz para que la máquina de estados de llaves ([SyncKeyManager])
 * se pruebe en JVM con un almacén en memoria — la implementación real
 * ([LocalDekStore]) envuelve con el Android Keystore, ausente en la JVM del
 * host (mismo criterio que `PayloadCipher`).
 */
interface DekStore {
    /** DEK local con la generación y la revisión del sobre publicado que la respalda. */
    data class StoredDek(val dek: ByteArray, val generation: Long, val revision: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredDek) return false
            return dek.contentEquals(other.dek) && generation == other.generation && revision == other.revision
        }

        override fun hashCode(): Int = 31 * (31 * dek.contentHashCode() + generation.toInt()) + revision.hashCode()
    }

    /**
     * DEK guardada para [uid], o null si no hay copia o pertenece a otra cuenta
     * (una cuenta nueva en este teléfono jamás hereda la llave de la anterior).
     * Lanza si el blob existe pero no descifra (corrupción, llave invalidada).
     */
    fun read(uid: String): StoredDek?

    /** Guarda (o reemplaza) la copia local de la DEK de [uid] con su sobre de referencia. */
    fun write(uid: String, dek: ByteArray, generation: Long, revision: String)

    /** Eliminación total (RF-20): llave envolvente primero, blob después. */
    fun erase()
}
