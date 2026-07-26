package com.auroraclima.app.core.security

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Implementación del «sobre doble» (RF-39; diseño aprobado en esta sesión).
 * [establish] es la única pieza que concilia el sobre publicado y la copia
 * local; corre bajo un mutex — dos confirmaciones de contraseña simultáneas no
 * existen en la UI, pero el estado de llaves jamás debe carrerear consigo mismo.
 *
 * Decisiones clave (ver ADR RF-39):
 * - Con generaciones al día NO se deriva nada: el login corriente no paga KDF.
 * - Publicar espera SIEMPRE la confirmación del servidor; el estado local solo
 *   avanza después — un fallo a mitad deja todo como estaba y el siguiente
 *   login reintenta o auto-repara.
 * - Reiniciar el respaldo (contraseña restablecida, sin copia local) purga los
 *   blobs viejos con los mismos erasers remotos de RF-20: son ilegibles para
 *   siempre y dejarlos sería solo residuo.
 */
@Singleton
class DefaultSyncKeyManager @Inject constructor(
    private val identity: AccountIdentity,
    private val remote: KeyEnvelopeRemoteSource,
    private val dekStore: DekStore,
    private val keyWrapper: PasswordKeyWrapper,
    private val remoteErasers: Set<@JvmSuppressWildcards RemoteAccountDataEraser>
) : SyncKeyManager {
    private val establishLock = Mutex()
    private val restartNotice = AtomicBoolean(false)

    override fun currentDek(): ByteArray? = withUid { uid -> dekStore.read(uid)?.dek }

    override fun currentGeneration(): Long? = withUid { uid -> dekStore.read(uid)?.generation }

    override suspend fun establish(password: String): Result<KeyEstablishment> = guarded {
        val uid = identity.currentUid() ?: throw IllegalStateException()
        val published = remote.fetch()
        val local = dekStore.read(uid)
        when {
            published == null -> publishFresh(uid, password, local)
            published.schemaVersion > PublishedKeyEnvelope.CURRENT_SCHEMA_VERSION ->
                // Formato futuro: no se toca — sobreescribirlo destruiría el
                // respaldo de una versión más nueva de la app.
                throw IllegalStateException()
            local != null && local.generation == published.generation && local.revision == published.revision ->
                // Sobre al día (misma DEK y misma publicación): sin KDF. La
                // revisión evita dar por bueno un sobre re-envuelto a medias
                // por un cambio de contraseña interrumpido.
                KeyEstablishment.Ready
            else -> reconcile(uid, password, published, local)
        }
    }

    override suspend fun rewrap(newPassword: String): Result<Unit> = guarded {
        val uid = identity.currentUid() ?: throw IllegalStateException()
        val local = dekStore.read(uid) ?: throw IllegalStateException()
        // Publica SIN tocar la copia local: la misma DEK (misma generación — los
        // registros no se re-cifran) con envoltura y revisión nuevas. Quien
        // orquesta el cambio de contraseña llama después a [establish] con la
        // contraseña que haya quedado activa: si el cambio culminó, adopta la
        // revisión nueva; si `updatePassword` falló, la auto-reparación
        // re-publica con la contraseña vigente. Ningún estado intermedio pasa
        // inadvertido porque la vía rápida exige la revisión al día.
        remote.publish(
            PublishedKeyEnvelope(keyWrapper.wrap(local.dek, newPassword), local.generation, freshRevision())
        )
    }

    override fun consumeRestartNotice(): Boolean = restartNotice.getAndSet(false)

    /** Primer sobre de la cuenta (o re-publicación si la nube lo perdió). */
    private suspend fun publishFresh(uid: String, password: String, local: DekStore.StoredDek?): KeyEstablishment {
        // Con copia local se conserva su generación: la DEK no cambia y los
        // registros ya subidos con ella siguen siendo válidos.
        val dek = local?.dek ?: freshDek()
        val generation = local?.generation ?: 1
        val revision = freshRevision()
        remote.publish(PublishedKeyEnvelope(keyWrapper.wrap(dek, password), generation, revision))
        dekStore.write(uid, dek, generation, revision)
        return KeyEstablishment.Ready
    }

    /** Generaciones desajustadas o sin copia local: hay que abrir el sobre publicado. */
    private suspend fun reconcile(
        uid: String,
        password: String,
        published: PublishedKeyEnvelope,
        local: DekStore.StoredDek?
    ): KeyEstablishment {
        val unwrapped = try {
            keyWrapper.unwrap(published.envelope, password)
        } catch (rejected: Exception) {
            null
        }
        return when {
            unwrapped != null -> {
                // La nube manda: adoptar la DEK publicada — restauración en un
                // dispositivo nuevo, rotación hecha desde otro aparato o cambio
                // de contraseña recién culminado en este.
                dekStore.write(uid, unwrapped, published.generation, published.revision)
                KeyEstablishment.Ready
            }
            local != null -> {
                // Auto-reparación: el sobre publicado no abre con la contraseña
                // vigente (cambio de contraseña interrumpido a mitad) pero la
                // DEK sigue aquí; se re-publica envuelta con la contraseña que
                // el proveedor acaba de confirmar. Misma generación: la DEK no
                // cambió y nada necesita re-subirse.
                val revision = freshRevision()
                remote.publish(
                    PublishedKeyEnvelope(keyWrapper.wrap(local.dek, password), local.generation, revision)
                )
                dekStore.write(uid, local.dek, local.generation, revision)
                KeyEstablishment.Ready
            }
            else -> restartBackup(uid, password, published)
        }
    }

    /**
     * Respaldo ilegible: contraseña restablecida sin conocer la anterior y sin
     * copia local que rescate (la pérdida verdaderamente inevitable del
     * esquema, documentada en el ADR y el informe). Purga primero — si falla,
     * nada avanza y el siguiente login re-entra aquí, idempotente — y solo
     * entonces publica el sobre nuevo y levanta el aviso de una sola vez.
     *
     * El documento de llaves NO se purga (auditoría D2 A-1): el `publish` lo
     * sobreescribe de todos modos, y borrarlo antes perdería el contador de
     * generación si el proceso muriera a mitad — el siguiente login partiría
     * como primer arranque en generación 1, chocando con las constancias del
     * dispositivo viejo, cuyo historial jamás volvería a subir. Conservarlo
     * hace la re-entrada tras un crash idempotente: siempre se suma sobre la
     * generación publicada.
     */
    private suspend fun restartBackup(
        uid: String,
        password: String,
        published: PublishedKeyEnvelope
    ): KeyEstablishment {
        remoteErasers
            .filterNot { it is SyncKeyRemoteEraser }
            .forEach { eraser -> eraser.erase().getOrThrow() }
        val dek = freshDek()
        val generation = published.generation + 1
        val revision = freshRevision()
        remote.publish(PublishedKeyEnvelope(keyWrapper.wrap(dek, password), generation, revision))
        dekStore.write(uid, dek, generation, revision)
        restartNotice.set(true)
        return KeyEstablishment.Restarted
    }

    private inline fun <T> withUid(block: (String) -> T?): T? = identity.currentUid()?.let(block)

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        establishLock.withLock {
            try {
                Result.success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Fallo silencioso (§3, regla 4): quien llama lo descarta y el
                // siguiente login reintenta desde un estado no avanzado.
                Result.failure(error)
            }
        }
    }

    private fun freshDek(): ByteArray = ByteArray(DEK_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

    /** Identificador opaco de cada publicación del sobre (hex aleatorio, sin semántica). */
    private fun freshRevision(): String {
        val bytes = ByteArray(REVISION_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DEK_SIZE_BYTES = 32
        const val REVISION_SIZE_BYTES = 8
    }
}
