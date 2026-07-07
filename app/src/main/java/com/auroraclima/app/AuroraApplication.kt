package com.auroraclima.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada de la aplicación. Inicializa el grafo de dependencias de Hilt.
 */
@HiltAndroidApp
class AuroraApplication : Application()
