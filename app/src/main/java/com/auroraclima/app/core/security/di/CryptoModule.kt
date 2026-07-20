package com.auroraclima.app.core.security.di

import com.auroraclima.app.core.security.KeystorePayloadCipher
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SyncKeyEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Enlaza el cifrador de payloads (RF-37). Vive en core: lo usará todo feature que suba datos cifrados. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    abstract fun bindPayloadCipher(impl: KeystorePayloadCipher): PayloadCipher

    /** La llave de los payloads muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindSyncKeyEraser(impl: SyncKeyEraser): LocalAccountDataEraser
}
