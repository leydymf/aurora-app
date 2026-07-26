package com.auroraclima.app.core.security

/**
 * Gobierno de la llave del respaldo cifrado (RF-39, «sobre doble»): una DEK
 * aleatoria cifra los payloads de sync; viaja a la nube envuelta por la
 * contraseña ([PasswordKeyWrapper] → [KeyEnvelopeRemoteSource]) y se conserva
 * localmente envuelta por el Keystore ([DekStore]) para que el uso corriente no
 * derive nada. El código secreto jamás participa (ADR de SQLCipher).
 *
 * [establish] corre en cada confirmación de contraseña (login RF-15, creación
 * de cuenta RF-07) y es quien concilia ambos sobres; el resto de la app solo
 * consume [currentDek]. Todo es de mejor esfuerzo aguas arriba: un fallo aquí
 * nunca rompe el login ni asoma un error revelador (§3).
 */
interface SyncKeyManager {
    /**
     * DEK vigente para sellar/abrir payloads, o null si aún no se establece en
     * este dispositivo. Lanza si la copia local existe pero no descifra
     * (corrupción del Keystore) — quien consume traduce a `Result` (§3).
     */
    fun currentDek(): ByteArray?

    /** Generación del sobre con el que se publicó [currentDek], o null sin DEK. */
    fun currentGeneration(): Long?

    /**
     * Concilia el sobre local y el publicado con la contraseña recién
     * confirmada por el proveedor. Cubre: primer arranque (publica el sobre),
     * dispositivo nuevo (desenvuelve y adopta la DEK), generaciones al día
     * (no deriva nada), sobre publicado atrasado (auto-reparación) y respaldo
     * ilegible por contraseña restablecida (reinicio del respaldo con purga).
     */
    suspend fun establish(password: String): Result<KeyEstablishment>

    /**
     * Re-envuelve la DEK local vigente con [newPassword] y publica el sobre
     * (misma generación — los registros NO se re-cifran; revisión nueva)
     * esperando al servidor. NO toca la copia local: quien orquesta el cambio
     * de contraseña llama después a [establish] con la contraseña que quedó
     * activa — adopción si el cambio culminó, auto-reparación si se
     * interrumpió. Falla sin tocar nada si no hay DEK local o el servidor no
     * confirma.
     */
    suspend fun rewrap(newPassword: String): Result<Unit>

    /**
     * True UNA sola vez si el último [establish] tuvo que reiniciar el respaldo
     * (contraseña restablecida sin la anterior, RF-39): la UI del historial
     * muestra el aviso genérico y el flag se consume.
     */
    fun consumeRestartNotice(): Boolean
}

/** Desenlace de [SyncKeyManager.establish]. */
sealed interface KeyEstablishment {
    /** DEK lista: creada, adoptada de la nube, al día o auto-reparada. */
    data object Ready : KeyEstablishment

    /**
     * El sobre publicado no abre con la contraseña vigente y este dispositivo
     * no tiene copia local: el respaldo anterior es ilegible (contraseña
     * restablecida). Se purgaron los blobs viejos y se publicó un sobre nuevo;
     * el historial parte de cero en la nube.
     */
    data object Restarted : KeyEstablishment
}

/**
 * Punto de extensión de la restauración multi-dispositivo (RF-39): tras
 * [SyncKeyManager.establish], cada feature repone lo suyo desde la nube vía
 * multibinding (mismo patrón que los erasers de RF-20). Mejor esfuerzo y
 * silencioso: un fallo no rompe el login y el siguiente inicio reintenta.
 */
fun interface AccountDataRestorer {
    suspend fun restore()
}
