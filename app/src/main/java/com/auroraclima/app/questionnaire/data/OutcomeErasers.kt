package com.auroraclima.app.questionnaire.data

import android.content.Context
import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.remote.OutcomePurgeRemoteSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte remoto del cuestionario a la eliminación total (RF-20): purga
 * confirmada de `outcomes` en la nube. Su fallo detiene la eliminación aguas
 * arriba — nada local se ha perdido y reintentar es idempotente.
 */
@Singleton
class OutcomeRemoteEraser @Inject constructor(
    private val purgeRemoteSource: OutcomePurgeRemoteSource
) : RemoteAccountDataEraser {
    override suspend fun erase(): Result<Unit> = networkResult { purgeRemoteSource.deleteAll() }
}

/**
 * Aporte local del cuestionario a la eliminación total (RF-20): el archivo del
 * almacén deja de existir. Aunque el borrado fallara, su passphrase ya la
 * destruyó el eraser de core/database — quedaría texto cifrado sin llave
 * (cripto-borrado), igual que el historial.
 */
@Singleton
class GuidanceStoreEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storeAccess: GuidanceStoreAccess
) : LocalAccountDataEraser {
    override suspend fun erase() {
        // Mejor esfuerzo por paso: un cierre que falle no debe impedir borrar el
        // archivo (deleteDatabase elimina también -wal y -shm).
        runCatching { storeAccess.close() }
        withContext(Dispatchers.IO) {
            runCatching { context.deleteDatabase(GuidanceDatabase.NAME) }
        }
    }
}
