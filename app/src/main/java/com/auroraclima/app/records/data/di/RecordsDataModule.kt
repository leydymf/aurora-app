package com.auroraclima.app.records.data.di

import com.auroraclima.app.core.database.EncryptedDatabaseFactory
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.core.security.SensitiveStateCleaner
import com.auroraclima.app.records.data.FirestoreJournalSyncRepository
import com.auroraclima.app.records.data.JournalDatabaseSource
import com.auroraclima.app.records.data.JournalMediaEraser
import com.auroraclima.app.records.data.JournalMediaSessionCleaner
import com.auroraclima.app.records.data.JournalRemoteEraser
import com.auroraclima.app.records.data.JournalStoreEraser
import com.auroraclima.app.records.data.LocalJournalAttachmentRepository
import com.auroraclima.app.records.data.LocalJournalRepository
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.remote.FirestoreJournalPurgeRemoteSource
import com.auroraclima.app.records.data.remote.FirestoreJournalSyncRemoteSource
import com.auroraclima.app.records.data.remote.JournalPurgeRemoteSource
import com.auroraclima.app.records.data.remote.JournalSyncRemoteSource
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordsDataModule {
    @Binds
    abstract fun bindJournalRepository(impl: LocalJournalRepository): JournalRepository

    /** Adjuntos protegidos (RF-33): misma BD cifrada + almacén de archivos cifrados. */
    @Binds
    abstract fun bindJournalAttachmentRepository(impl: LocalJournalAttachmentRepository): JournalAttachmentRepository

    @Binds
    abstract fun bindJournalSyncRepository(impl: FirestoreJournalSyncRepository): JournalSyncRepository

    @Binds
    abstract fun bindJournalSyncRemoteSource(impl: FirestoreJournalSyncRemoteSource): JournalSyncRemoteSource

    @Binds
    abstract fun bindJournalPurgeRemoteSource(impl: FirestoreJournalPurgeRemoteSource): JournalPurgeRemoteSource

    /** RF-20: los registros mueren con la cuenta — purga confirmada en la nube… */
    @Binds
    @IntoSet
    abstract fun bindJournalRemoteEraser(impl: JournalRemoteEraser): RemoteAccountDataEraser

    /** …archivo local de la BD cifrada… */
    @Binds
    @IntoSet
    abstract fun bindJournalStoreEraser(impl: JournalStoreEraser): LocalAccountDataEraser

    /** …y almacén de adjuntos cifrados (RF-33). */
    @Binds
    @IntoSet
    abstract fun bindJournalMediaEraser(impl: JournalMediaEraser): LocalAccountDataEraser

    /** Todo cierre de sesión barre los temporales de adjuntos (RF-33, auditoría E1 M-2). */
    @Binds
    @IntoSet
    abstract fun bindJournalMediaSessionCleaner(impl: JournalMediaSessionCleaner): SensitiveStateCleaner

    companion object {
        /**
         * Solo captura la fábrica: NO construye la BD aquí (P-36) — la apertura
         * hace I/O y puede lanzar, y eso ocurre recién dentro del repositorio,
         * en su dispatcher de I/O y traducido a Result. La migración v1→v2
         * (adjuntos, RF-33) conserva el historial ya guardado.
         */
        @Provides
        fun provideJournalDatabaseSource(factory: EncryptedDatabaseFactory): JournalDatabaseSource =
            JournalDatabaseSource {
                factory.create(JournalDatabase::class.java, JournalDatabase.NAME, JournalDatabase.MIGRATION_1_2)
            }
    }
}
