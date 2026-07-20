package com.auroraclima.app.core.security.di

import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.PendingErasureFlag
import com.auroraclima.app.core.security.PendingErasureRecovery
import com.auroraclima.app.core.security.PrefsPendingErasureFlag
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.core.startup.AppInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class ErasureModule {
    /** Conjuntos vacíos por defecto; cada dueño de datos aporta el suyo con @IntoSet (RF-20). */
    @Multibinds
    abstract fun remoteAccountDataErasers(): Set<RemoteAccountDataEraser>

    @Multibinds
    abstract fun localAccountDataErasers(): Set<LocalAccountDataEraser>

    @Binds
    abstract fun bindPendingErasureFlag(impl: PrefsPendingErasureFlag): PendingErasureFlag

    /** El arranque completa un borrado local interrumpido (auditoría H-1). */
    @Binds
    @IntoSet
    abstract fun bindPendingErasureRecovery(impl: PendingErasureRecovery): AppInitializer
}
