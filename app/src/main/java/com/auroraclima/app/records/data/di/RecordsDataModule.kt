package com.auroraclima.app.records.data.di

import android.content.Context
import android.net.Uri
import com.auroraclima.app.core.database.EncryptedDatabaseFactory
import com.auroraclima.app.core.security.AccountDataReconciler
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.core.security.SensitiveStateCleaner
import com.auroraclima.app.core.startup.AppInitializer
import com.auroraclima.app.records.data.FirestoreJournalMediaSyncRepository
import com.auroraclima.app.records.data.FirestoreJournalSyncRepository
import com.auroraclima.app.records.data.JournalDatabaseSource
import com.auroraclima.app.records.data.JournalMediaEraser
import com.auroraclima.app.records.data.JournalMediaSessionCleaner
import com.auroraclima.app.records.data.JournalRemoteEraser
import com.auroraclima.app.records.data.JournalStoreEraser
import com.auroraclima.app.records.data.JournalSyncReconciler
import com.auroraclima.app.records.data.LocalJournalAttachmentRepository
import com.auroraclima.app.records.data.LocalJournalRepository
import com.auroraclima.app.records.data.export.ExportStagingSweeper
import com.auroraclima.app.records.data.export.FileProviderShareUriResolver
import com.auroraclima.app.records.data.export.LocalJournalExportRepository
import com.auroraclima.app.records.data.export.OutputStreamOpener
import com.auroraclima.app.records.data.export.PdfBoxExportWriter
import com.auroraclima.app.records.data.export.ShareUriResolver
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.remote.FirestoreJournalMediaRemoteSource
import com.auroraclima.app.records.data.remote.FirestoreJournalPurgeRemoteSource
import com.auroraclima.app.records.data.remote.FirestoreJournalSyncRemoteSource
import com.auroraclima.app.records.data.remote.JournalMediaRemoteSource
import com.auroraclima.app.records.data.remote.JournalPurgeRemoteSource
import com.auroraclima.app.records.data.remote.JournalSyncRemoteSource
import com.auroraclima.app.records.domain.model.DayProvider
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalExportRepository
import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDate

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordsDataModule {
    @Binds
    abstract fun bindJournalRepository(impl: LocalJournalRepository): JournalRepository

    /** Adjuntos protegidos (RF-33): misma BD cifrada + almacén de archivos cifrados. */
    @Binds
    abstract fun bindJournalAttachmentRepository(impl: LocalJournalAttachmentRepository): JournalAttachmentRepository

    /** RF-45: PDF cifrado hacia el selector del sistema (guardar) o un temporal (compartir). */
    @Binds
    abstract fun bindJournalExportRepository(impl: LocalJournalExportRepository): JournalExportRepository

    /** La librería de PDF (ADR RF-45) queda detrás del contrato de dominio. */
    @Binds
    abstract fun bindExportDocumentWriter(impl: PdfBoxExportWriter): ExportDocumentWriter

    @Binds
    abstract fun bindShareUriResolver(impl: FileProviderShareUriResolver): ShareUriResolver

    /** RF-45: al arrancar el proceso no queda ningún PDF temporal de una sesión interrumpida. */
    @Binds
    @IntoSet
    abstract fun bindExportStagingSweeper(impl: ExportStagingSweeper): AppInitializer

    @Binds
    abstract fun bindJournalSyncRepository(impl: FirestoreJournalSyncRepository): JournalSyncRepository

    @Binds
    abstract fun bindJournalSyncRemoteSource(impl: FirestoreJournalSyncRemoteSource): JournalSyncRemoteSource

    /** RF-39: las evidencias también viajan cifradas, troceadas por el límite de 1 MiB. */
    @Binds
    abstract fun bindJournalMediaSyncRepository(impl: FirestoreJournalMediaSyncRepository): JournalMediaSyncRepository

    @Binds
    abstract fun bindJournalMediaRemoteSource(impl: FirestoreJournalMediaRemoteSource): JournalMediaRemoteSource

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

    /** RF-39: tras cada login, el historial se reconcilia con la nube (migración y re-subidas). */
    @Binds
    @IntoSet
    abstract fun bindJournalSyncReconciler(impl: JournalSyncReconciler): AccountDataRestorer

    /** ADR 2026-10-06: y además trae lo que se creó, editó o eliminó en otro dispositivo. */
    @Binds
    @IntoSet
    abstract fun bindJournalCloudReconciler(impl: JournalSyncReconciler): AccountDataReconciler

    companion object {
        /** Fecha civil del dispositivo para nombrar y fechar la exportación (RF-45). */
        @Provides
        fun provideDayProvider(): DayProvider = DayProvider { LocalDate.now() }

        /** El destino de «Guardar» (RF-45) lo abre el ContentResolver con el URI que devolvió el selector. */
        @Provides
        fun provideOutputStreamOpener(@ApplicationContext context: Context): OutputStreamOpener =
            OutputStreamOpener { uri -> context.contentResolver.openOutputStream(Uri.parse(uri), "wt") }

        /**
         * Solo captura la fábrica: NO construye la BD aquí (P-36) — la apertura
         * hace I/O y puede lanzar, y eso ocurre recién dentro del repositorio,
         * en su dispatcher de I/O y traducido a Result. Las migraciones v1→v2
         * (adjuntos, RF-33), v2→v3 y v3→v4 (constancia de sync, RF-39), v4→v5
         * (tipos por id de tema, P-68) y v5→v6 (nombre del documento en la nube,
         * fecha de edición y constancias de borrado, ADR 2026-10-06) conservan
         * el historial ya guardado.
         */
        @Provides
        fun provideJournalDatabaseSource(factory: EncryptedDatabaseFactory): JournalDatabaseSource =
            JournalDatabaseSource {
                factory.create(
                    JournalDatabase::class.java,
                    JournalDatabase.NAME,
                    JournalDatabase.MIGRATION_1_2,
                    JournalDatabase.MIGRATION_2_3,
                    JournalDatabase.MIGRATION_3_4,
                    JournalDatabase.MIGRATION_4_5,
                    JournalDatabase.MIGRATION_5_6
                )
            }
    }
}
