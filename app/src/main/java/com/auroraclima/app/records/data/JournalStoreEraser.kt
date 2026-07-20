package com.auroraclima.app.records.data

import android.content.Context
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.records.data.local.JournalDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte local de records a la eliminación total (RF-20): el archivo de la BD
 * cifrada deja de existir. Aunque el borrado del archivo fallara, su passphrase
 * ya la destruyó el eraser de core/database — quedaría solo texto cifrado sin
 * llave (cripto-borrado).
 */
@Singleton
class JournalStoreEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LocalJournalRepository
) : LocalAccountDataEraser {
    override suspend fun erase() {
        // Mejor esfuerzo por paso: un cierre que falle no debe impedir borrar el
        // archivo (deleteDatabase elimina también -wal y -shm).
        runCatching { repository.close() }
        withContext(Dispatchers.IO) {
            runCatching { context.deleteDatabase(JournalDatabase.NAME) }
        }
    }
}
