package com.auroraclima.app.core.security.di

import com.auroraclima.app.core.security.AccountDataReconciler
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.CloudReconciliation
import com.auroraclima.app.core.security.CloudReconciliationCoordinator
import com.auroraclima.app.core.security.CloudReconciliationScope
import com.auroraclima.app.core.security.DefaultSyncKeyManager
import com.auroraclima.app.core.security.DekMediaCipher
import com.auroraclima.app.core.security.DekPayloadCipher
import com.auroraclima.app.core.security.DekStore
import com.auroraclima.app.core.security.KeystoreMediaCipher
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.LocalDekStore
import com.auroraclima.app.core.security.MediaCipher
import com.auroraclima.app.core.security.MediaKeyEraser
import com.auroraclima.app.core.security.OpaqueIdSource
import com.auroraclima.app.core.security.PasswordKeyDerivation
import com.auroraclima.app.core.security.PasswordKeyWrapper
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.Pbkdf2PasswordKeyDerivation
import com.auroraclima.app.core.security.RecoverableMediaCipher
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.core.security.SecureRandomOpaqueIds
import com.auroraclima.app.core.security.SensitiveStateCleaner
import com.auroraclima.app.core.security.SyncKeyEraser
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.core.security.SyncKeyRemoteEraser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Enlaza los cifradores de core (RF-37, RF-33, RF-39). Viven en core: los usa todo feature que cifre datos propios. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    /** RF-39: el payload de sync se sella con la DEK recuperable, ya no con la llave del dispositivo. */
    @Binds
    abstract fun bindPayloadCipher(impl: DekPayloadCipher): PayloadCipher

    @Binds
    abstract fun bindMediaCipher(impl: KeystoreMediaCipher): MediaCipher

    /** RF-39: el viaje de las evidencias a la nube se sella con la DEK recuperable. */
    @Binds
    abstract fun bindRecoverableMediaCipher(impl: DekMediaCipher): RecoverableMediaCipher

    @Binds
    abstract fun bindSyncKeyManager(impl: DefaultSyncKeyManager): SyncKeyManager

    @Binds
    abstract fun bindDekStore(impl: LocalDekStore): DekStore

    @Binds
    abstract fun bindPasswordKeyDerivation(impl: Pbkdf2PasswordKeyDerivation): PasswordKeyDerivation

    /** Nombres de documento opacos para todo lo que viaja a la nube (ADR 2026-10-06). */
    @Binds
    abstract fun bindOpaqueIdSource(impl: SecureRandomOpaqueIds): OpaqueIdSource

    /** Disparo de la reconciliación con la nube (ADR 2026-10-06). */
    @Binds
    abstract fun bindCloudReconciliation(impl: CloudReconciliationCoordinator): CloudReconciliation

    /** Toda salida del espacio interno cancela la reconciliación en vuelo. */
    @Binds
    @IntoSet
    abstract fun bindCloudReconciliationCleaner(impl: CloudReconciliationCoordinator): SensitiveStateCleaner

    // KeyEnvelopeRemoteSource se bindea en su implementador (core/network,
    // FirestoreBindingsModule) — mismo criterio que FirestoreCacheEraser.

    /** Las llaves de los payloads (legado y sobre doble) mueren con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindSyncKeyEraser(impl: SyncKeyEraser): LocalAccountDataEraser

    /** La llave del almacén de adjuntos también (RF-20, cripto-borrado). */
    @Binds
    @IntoSet
    abstract fun bindMediaKeyEraser(impl: MediaKeyEraser): LocalAccountDataEraser

    /** El documento de llaves también muere con la cuenta (RF-20, RF-39). */
    @Binds
    @IntoSet
    abstract fun bindSyncKeyRemoteEraser(impl: SyncKeyRemoteEraser): RemoteAccountDataEraser

    /** Conjunto de restauradores (RF-39): cada feature aporta el suyo vía @IntoSet. */
    @Multibinds
    abstract fun accountDataRestorers(): Set<AccountDataRestorer>

    /** Conjunto de reconciliadores (ADR 2026-10-06), con el mismo criterio. */
    @Multibinds
    abstract fun accountDataReconcilers(): Set<AccountDataReconciler>

    companion object {
        /**
         * Scope propio de la reconciliación en segundo plano (ADR 2026-10-06):
         * el inicio de sesión la pide y sigue adelante sin esperarla. Vive lo
         * que vive el proceso; cada trabajo es idempotente y silencioso.
         */
        @Provides
        @Singleton
        @CloudReconciliationScope
        fun provideCloudReconciliationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Sobre de la DEK con las iteraciones de producción (ver KDoc del wrapper). */
        @Provides
        fun providePasswordKeyWrapper(keyDerivation: PasswordKeyDerivation): PasswordKeyWrapper =
            PasswordKeyWrapper(keyDerivation)
    }
}
