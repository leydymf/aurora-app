package com.auroraclima.app.accounts.data.di

import com.auroraclima.app.accounts.data.AuthService
import com.auroraclima.app.accounts.data.AuthSessionCleaner
import com.auroraclima.app.accounts.data.FirebaseAuthRepository
import com.auroraclima.app.accounts.data.FirebaseAuthService
import com.auroraclima.app.accounts.data.LocalCredentialStore
import com.auroraclima.app.accounts.data.PrefsSignInAttemptStore
import com.auroraclima.app.accounts.data.SignInAttemptsEraser
import com.auroraclima.app.accounts.data.SilentSessionCoordinator
import com.auroraclima.app.accounts.data.StoredCredentialsEraser
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.security.WallClock
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.SensitiveStateCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ámbito del reingreso silencioso (P-31 opción C): sobrevive al pop de los
 * ViewModels del grafo seguro; inyectable en tests con un TestScope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SilentSessionScope

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

    /** Blob de credenciales del reingreso silencioso (P-31 opción C). */
    @Binds
    abstract fun bindCredentialStore(impl: LocalCredentialStore): CredentialStore

    /** El blob de credenciales muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindStoredCredentialsEraser(impl: StoredCredentialsEraser): LocalAccountDataEraser

    /** El contador de intentos muere con la cuenta (RF-20). */
    @Binds
    @IntoSet
    abstract fun bindSignInAttemptsEraser(impl: SignInAttemptsEraser): LocalAccountDataEraser

    /** Toda salida del espacio interno cancela la confirmación en curso (P-31 C). */
    @Binds
    @IntoSet
    abstract fun bindSilentSessionCleaner(impl: SilentSessionCoordinator): SensitiveStateCleaner

    companion object {
        // FirebaseAuth se provee en core/network/FirestoreModule: lo comparten
        // accounts y records (§2, RNF-06), como ya ocurre con Firestore.

        /** Reloj de pared del bloqueo de RF-15 (ver KDoc de [WallClock]). */
        @Provides
        fun provideWallClock(): WallClock = WallClock { System.currentTimeMillis() }

        /** Main.immediate: el coordinador es main-confined, como SessionManager. */
        @Provides
        @Singleton
        @SilentSessionScope
        fun provideSilentSessionScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
