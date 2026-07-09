package com.auroraclima.app

import android.app.Application
import com.auroraclima.app.core.startup.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la aplicación. Inicializa el grafo de dependencias de Hilt y
 * ejecuta las tareas de arranque aportadas por cada build type (multibinding).
 */
@HiltAndroidApp
class AuroraApplication : Application() {
    @Inject
    lateinit var initializers: Set<@JvmSuppressWildcards AppInitializer>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            initializers.forEach { it.initialize() }
        }
    }
}
