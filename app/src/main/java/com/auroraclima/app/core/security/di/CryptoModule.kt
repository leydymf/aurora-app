package com.auroraclima.app.core.security.di

import com.auroraclima.app.core.security.KeystoreMediaCipher
import com.auroraclima.app.core.security.KeystorePayloadCipher
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.MediaCipher
import com.auroraclima.app.core.security.MediaKeyEraser
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SyncKeyEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Enlaza los cifradores de core (RF-37, RF-33). Viven en core: los usa todo feature que cifre datos propios. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    abstract fun bindPayloadCipher(impl: KeystorePayloadCipher): PayloadCipher

    @Binds
    abstract fun bindMediaCipher(impl: KeystoreMediaCipher): MediaCipher

    /** La llave de los payloads muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindSyncKeyEraser(impl: SyncKeyEraser): LocalAccountDataEraser

    /** La llave del almacén de adjuntos también (RF-20, cripto-borrado). */
    @Binds
    @IntoSet
    abstract fun bindMediaKeyEraser(impl: MediaKeyEraser): LocalAccountDataEraser
}
