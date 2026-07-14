package com.auroraclima.app.records.data.di

import com.auroraclima.app.core.database.EncryptedDatabaseFactory
import com.auroraclima.app.records.data.JournalDatabaseSource
import com.auroraclima.app.records.data.LocalJournalRepository
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.domain.repository.JournalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordsDataModule {
    @Binds
    abstract fun bindJournalRepository(impl: LocalJournalRepository): JournalRepository

    companion object {
        /**
         * Solo captura la fábrica: NO construye la BD aquí (P-36) — la apertura
         * hace I/O y puede lanzar, y eso ocurre recién dentro del repositorio,
         * en su dispatcher de I/O y traducido a Result.
         */
        @Provides
        fun provideJournalDatabaseSource(factory: EncryptedDatabaseFactory): JournalDatabaseSource =
            JournalDatabaseSource { factory.create(JournalDatabase::class.java, JournalDatabase.NAME) }
    }
}
