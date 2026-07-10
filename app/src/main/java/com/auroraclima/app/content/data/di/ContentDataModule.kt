package com.auroraclima.app.content.data.di

import com.auroraclima.app.content.data.FirestoreContentRepository
import com.auroraclima.app.content.data.remote.ContentRemoteSource
import com.auroraclima.app.content.data.remote.FirestoreContentRemoteSource
import com.auroraclima.app.content.domain.repository.ContentRepository
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
}
