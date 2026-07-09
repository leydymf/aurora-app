package com.auroraclima.app.access.data.di

import com.auroraclima.app.access.data.KeystoreSecretCodeHasher
import com.auroraclima.app.access.data.LocalSecretCodeRepository
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.security.SearchQueryGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessDataModule {
    @Binds
    abstract fun bindSecretCodeHasher(impl: KeystoreSecretCodeHasher): SecretCodeHasher

    @Binds
    abstract fun bindSecretCodeRepository(impl: LocalSecretCodeRepository): SecretCodeRepository

    /** La puerta de la barra de búsqueda (core) se resuelve con el caso de uso de access. */
    @Binds
    abstract fun bindSearchQueryGate(impl: ValidateSecretCodeUseCase): SearchQueryGate
}
