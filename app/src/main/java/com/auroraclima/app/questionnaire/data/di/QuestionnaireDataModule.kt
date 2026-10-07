package com.auroraclima.app.questionnaire.data.di

import com.auroraclima.app.core.database.EncryptedDatabaseFactory
import com.auroraclima.app.core.security.AccountDataReconciler
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.questionnaire.data.FirestoreOutcomeSyncRepository
import com.auroraclima.app.questionnaire.data.FirestoreQuestionnaireRepository
import com.auroraclima.app.questionnaire.data.GuidanceDatabaseSource
import com.auroraclima.app.questionnaire.data.GuidanceStoreEraser
import com.auroraclima.app.questionnaire.data.LocalSavedOutcomeRepository
import com.auroraclima.app.questionnaire.data.OutcomeRemoteEraser
import com.auroraclima.app.questionnaire.data.OutcomeSyncReconciler
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.remote.FirestoreOutcomePurgeRemoteSource
import com.auroraclima.app.questionnaire.data.remote.FirestoreOutcomeRemoteSource
import com.auroraclima.app.questionnaire.data.remote.FirestoreQuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.data.remote.OutcomePurgeRemoteSource
import com.auroraclima.app.questionnaire.data.remote.OutcomeSyncRemoteSource
import com.auroraclima.app.questionnaire.data.remote.QuestionnaireRemoteSource
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class QuestionnaireDataModule {
    @Binds
    abstract fun bindQuestionnaireRemoteSource(impl: FirestoreQuestionnaireRemoteSource): QuestionnaireRemoteSource

    @Binds
    abstract fun bindQuestionnaireRepository(impl: FirestoreQuestionnaireRepository): QuestionnaireRepository

    /** Resultados guardados (RF-31): almacén cifrado propio + sync con la DEK de RF-39. */
    @Binds
    abstract fun bindSavedOutcomeRepository(impl: LocalSavedOutcomeRepository): SavedOutcomeRepository

    @Binds
    abstract fun bindOutcomeSyncRepository(impl: FirestoreOutcomeSyncRepository): OutcomeSyncRepository

    @Binds
    abstract fun bindOutcomeSyncRemoteSource(impl: FirestoreOutcomeRemoteSource): OutcomeSyncRemoteSource

    @Binds
    abstract fun bindOutcomePurgeRemoteSource(impl: FirestoreOutcomePurgeRemoteSource): OutcomePurgeRemoteSource

    /** RF-20: los resultados guardados mueren con la cuenta — purga confirmada en la nube… */
    @Binds
    @IntoSet
    abstract fun bindOutcomeRemoteEraser(impl: OutcomeRemoteEraser): RemoteAccountDataEraser

    /** …y archivo local del almacén cifrado. */
    @Binds
    @IntoSet
    abstract fun bindGuidanceStoreEraser(impl: GuidanceStoreEraser): LocalAccountDataEraser

    /** RF-39: tras cada login, los guardados se reconcilian con la nube. */
    @Binds
    @IntoSet
    abstract fun bindOutcomeSyncReconciler(impl: OutcomeSyncReconciler): AccountDataRestorer

    /** ADR 2026-10-06: y además traen lo que se guardó o eliminó en otro dispositivo. */
    @Binds
    @IntoSet
    abstract fun bindOutcomeCloudReconciler(impl: OutcomeSyncReconciler): AccountDataReconciler

    companion object {
        /**
         * Solo captura la fábrica: NO construye la BD aquí (P-36) — la apertura
         * hace I/O y puede lanzar, y eso ocurre recién dentro del acceso, en su
         * dispatcher de I/O y traducido a Result. La migración v1→v2 (nombre
         * del documento en la nube y constancias de borrado, ADR 2026-10-06)
         * conserva los resultados ya guardados.
         */
        @Provides
        fun provideGuidanceDatabaseSource(factory: EncryptedDatabaseFactory): GuidanceDatabaseSource =
            GuidanceDatabaseSource {
                factory.create(
                    GuidanceDatabase::class.java,
                    GuidanceDatabase.NAME,
                    GuidanceDatabase.MIGRATION_1_2
                )
            }
    }
}
