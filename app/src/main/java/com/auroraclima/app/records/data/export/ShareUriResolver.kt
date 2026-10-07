package com.auroraclima.app.records.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Traduce el temporal a un URI `content://` del FileProvider no exportado.
 * Contrato aparte porque `FileProvider` exige el manifest real y guarda sus
 * rutas en un caché estático: en la JVM se sustituye por un falso y la
 * construcción real se verifica instrumentada (`JournalExportPipelineTest`).
 */
fun interface ShareUriResolver {
    fun uriFor(file: File): Uri
}

/** Resolución real: la autoridad se deriva del applicationId, igual que el manifest. */
class FileProviderShareUriResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : ShareUriResolver {
    override fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

    private companion object {
        const val AUTHORITY_SUFFIX = ".files"
    }
}
