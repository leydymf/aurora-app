package com.auroraclima.app.accounts.data.di

import com.auroraclima.app.accounts.data.DebugAuthSessionSeeder
import com.auroraclima.app.core.startup.AppInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Solo builds debug: registra la sesión de prueba para leer contenido. TODO(RF-15): eliminar. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugAccountsModule {
    @Binds
    @IntoSet
    abstract fun bindDebugAuthSessionSeeder(impl: DebugAuthSessionSeeder): AppInitializer
}
