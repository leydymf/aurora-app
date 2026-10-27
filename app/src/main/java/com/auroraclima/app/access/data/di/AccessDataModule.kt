package com.auroraclima.app.access.data.di

import com.auroraclima.app.access.data.AccessCredentialsEraser
import com.auroraclima.app.access.data.HomeTourEraser
import com.auroraclima.app.access.data.KeystoreSecretCodeHasher
import com.auroraclima.app.access.data.LocalSecretCodeRepository
import com.auroraclima.app.access.data.RawCityNameDirectory
import com.auroraclima.app.access.data.SealedHomeTourRepository
import com.auroraclima.app.access.domain.repository.CityNameDirectory
import com.auroraclima.app.access.domain.repository.HomeTourRepository
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.access.domain.usecase.CheckAccessCodeUseCase
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.security.AccessCodePresence
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.SearchQueryGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessDataModule {
    @Binds
    abstract fun bindSecretCodeHasher(impl: KeystoreSecretCodeHasher): SecretCodeHasher

    @Binds
    abstract fun bindSecretCodeRepository(impl: LocalSecretCodeRepository): SecretCodeRepository

    @Binds
    abstract fun bindCityNameDirectory(impl: RawCityNameDirectory): CityNameDirectory

    /** La puerta de la barra de búsqueda (core) se resuelve con el caso de uso de access. */
    @Binds
    abstract fun bindSearchQueryGate(impl: ValidateSecretCodeUseCase): SearchQueryGate

    /** ¿Existe ya el código de acceso? (accounts y la sync lo consultan desde core). */
    @Binds
    abstract fun bindAccessCodePresence(impl: CheckAccessCodeUseCase): AccessCodePresence

    /** La puerta (hashes y su llave) muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindAccessCredentialsEraser(impl: AccessCredentialsEraser): LocalAccountDataEraser

    /** Recorrido por Inicio de la primera llegada tras la activación. */
    @Binds
    abstract fun bindHomeTourRepository(impl: SealedHomeTourRepository): HomeTourRepository

    /** El recorrido pendiente muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindHomeTourEraser(impl: HomeTourEraser): LocalAccountDataEraser
}
