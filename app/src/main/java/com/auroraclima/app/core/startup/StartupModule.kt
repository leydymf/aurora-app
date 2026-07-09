package com.auroraclima.app.core.startup

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class StartupModule {
    /** Conjunto vacío por defecto; cada build type aporta los suyos con @IntoSet. */
    @Multibinds
    abstract fun appInitializers(): Set<AppInitializer>
}
