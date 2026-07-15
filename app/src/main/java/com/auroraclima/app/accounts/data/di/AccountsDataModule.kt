package com.auroraclima.app.accounts.data.di

import com.auroraclima.app.accounts.data.AuthService
import com.auroraclima.app.accounts.data.AuthSessionCleaner
import com.auroraclima.app.accounts.data.FirebaseAuthRepository
import com.auroraclima.app.accounts.data.FirebaseAuthService
import com.auroraclima.app.accounts.data.PrefsSignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.security.WallClock
import com.auroraclima.app.core.security.SensitiveStateCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountsDataModule {
    @Binds
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    /** La sesión del proveedor no sobrevive al cierre del espacio interno (D3 H-3). */
    @Binds
    @IntoSet
    abstract fun bindAuthSessionCleaner(impl: AuthSessionCleaner): SensitiveStateCleaner

    @Binds
    abstract fun bindSignInAttemptStore(impl: PrefsSignInAttemptStore): SignInAttemptStore

    companion object {
        // FirebaseAuth se provee en core/network/FirestoreModule: lo comparten
        // accounts y records (§2, RNF-06), como ya ocurre con Firestore.

        /** Reloj de pared del bloqueo de RF-15 (ver KDoc de [WallClock]). */
        @Provides
        fun provideWallClock(): WallClock = WallClock { System.currentTimeMillis() }
    }
}
