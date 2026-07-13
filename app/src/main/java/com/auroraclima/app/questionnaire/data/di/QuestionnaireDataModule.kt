package com.auroraclima.app.questionnaire.data.di

import com.auroraclima.app.questionnaire.data.FirestoreQuestionnaireRepository
import com.auroraclima.app.questionnaire.data.remote.FirestoreQuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class QuestionnaireDataModule {
    @Binds
    abstract fun bindQuestionnaireRemoteSource(impl: FirestoreQuestionnaireRemoteSource): QuestionnaireRemoteSource

    @Binds
    abstract fun bindQuestionnaireRepository(impl: FirestoreQuestionnaireRepository): QuestionnaireRepository
}
