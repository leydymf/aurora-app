package com.auroraclima.app.content.data.di

import com.auroraclima.app.content.data.DirectoryContentProvider
import com.auroraclima.app.content.data.FirestoreContentRepository
import com.auroraclima.app.content.data.remote.ContentRemoteSource
import com.auroraclima.app.content.data.remote.FirestoreContentRemoteSource
import com.auroraclima.app.content.domain.repository.ContentRepository
import com.auroraclima.app.core.directory.DirectoryProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentDataModule {
    @Binds
    abstract fun bindContentRemoteSource(impl: FirestoreContentRemoteSource): ContentRemoteSource

    @Binds
    abstract fun bindContentRepository(impl: FirestoreContentRepository): ContentRepository

    // Accesos directos del cuestionario (RF-29 §6): content resuelve las
    // referencias de líneas y entidades a través del contrato de core (§2).
    @Binds
    abstract fun bindDirectoryProvider(impl: DirectoryContentProvider): DirectoryProvider
}
