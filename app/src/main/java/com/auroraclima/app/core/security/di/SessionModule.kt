package com.auroraclima.app.core.security.di

import android.os.SystemClock
import com.auroraclima.app.core.security.MonotonicClock
import com.auroraclima.app.core.security.SensitiveStateCleaner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ámbito de los temporizadores de sesión; inyectable en tests con un TestScope. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionTimerScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    /**
     * Conjunto vacío por defecto; cada feature aporta sus cleaners con @IntoSet
     * (punto de extensión de RF-28 y siguientes).
     */
    @Multibinds
    abstract fun sensitiveStateCleaners(): Set<SensitiveStateCleaner>

    companion object {
        /** Main.immediate: SessionManager es main-confined por diseño. */
        @Provides
        @Singleton
        @SessionTimerScope
        fun provideSessionTimerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** Cuenta también la suspensión profunda (respaldo de la gracia, auditoría H-3). */
        @Provides
        fun provideMonotonicClock(): MonotonicClock = MonotonicClock(SystemClock::elapsedRealtime)
    }
}
