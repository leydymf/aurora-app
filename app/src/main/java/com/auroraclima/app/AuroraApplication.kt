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
    // Lazy y no Set: así la CONSTRUCCIÓN de los inicializadores (y de todo lo que
    // arrastren) ocurre dentro del launch, fuera del hilo principal (ADR 2026-10-05).
    @Inject
    lateinit var initializers: dagger.Lazy<Set<@JvmSuppressWildcards AppInitializer>>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            initializers.get().forEach { it.initialize() }
        }
    }
}
