package com.auroraclima.app.access.data.di

import com.auroraclima.app.access.data.DebugSecretCodeSeeder
import com.auroraclima.app.core.startup.AppInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Solo builds debug: registra la siembra provisional de códigos. TODO(RF-08): eliminar. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugAccessModule {
    @Binds
    @IntoSet
    abstract fun bindDebugSecretCodeSeeder(impl: DebugSecretCodeSeeder): AppInitializer
}
