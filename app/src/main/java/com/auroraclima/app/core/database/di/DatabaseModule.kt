package com.auroraclima.app.core.database.di

import com.auroraclima.app.core.database.DatabaseKeyEraser
import com.auroraclima.app.core.security.LocalAccountDataEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    /** La passphrase de la BD cifrada muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindDatabaseKeyEraser(impl: DatabaseKeyEraser): LocalAccountDataEraser
}
