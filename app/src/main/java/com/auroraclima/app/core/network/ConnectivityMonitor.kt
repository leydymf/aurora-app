package com.auroraclima.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Señal de conectividad del dispositivo (P-31 opción C, RNF-08): el reingreso
 * silencioso la usa para distinguir «sin red» (se opera en local, sin pantalla
 * de contraseña) de «con red» (confirmación en segundo plano) y para reintentar
 * cuando la conexión regresa. Interfaz propia para poder simularla en tests.
 */
interface ConnectivityMonitor {
    /** Emite el estado actual al suscribirse y cada cambio posterior, sin repetidos. */
    val isOnline: Flow<Boolean>
}

/**
 * Implementación sobre [ConnectivityManager] (permiso ACCESS_NETWORK_STATE, de
 * nivel normal y esperable en una app de clima — RNF-04/RNF-05). Sin registro
 * permanente: el callback vive solo mientras alguien colecta el flujo.
 */
@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.isCurrentlyOnline())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
