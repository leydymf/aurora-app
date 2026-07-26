package com.auroraclima.app.core.network

import com.auroraclima.app.core.security.KeyEnvelopeRemoteSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings de las implementaciones Firestore de core/network hacia las
 * interfaces puras de core/security (revisión de arquitectura D2): el módulo
 * del implementador aporta el enlace — mismo criterio que `FirestoreCacheEraser`
 * en `FirestoreModule` —, así `core/security` no compila contra este paquete.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreBindingsModule {
    /** Documento de llaves del respaldo (RF-39). */
    @Binds
    abstract fun bindKeyEnvelopeRemoteSource(impl: FirestoreKeyEnvelopeRemoteSource): KeyEnvelopeRemoteSource
}
