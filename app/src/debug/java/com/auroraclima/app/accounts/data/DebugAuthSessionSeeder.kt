package com.auroraclima.app.accounts.data

import com.auroraclima.app.core.startup.AppInitializer
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Sesión de prueba SOLO para builds debug: el contenido informativo exige
 * lectura autenticada (`firestore.rules`) y el inicio de sesión real llega
 * con RF-15, así que sin esto el módulo de información no es probable en
 * emulador. Inicia sesión con una cuenta fija al arrancar el proceso (la
 * crea la primera vez); usa el SDK directo como [FirebaseAuthService].
 *
 * Caveat: `AuthSessionCleaner` cierra la sesión del proveedor en cada
 * `closeSession()` (salida rápida, inactividad, background prolongado) y
 * este seeder corre solo al arrancar — tras una salida, reinicia la app
 * (`force-stop`) para recuperar la sesión de prueba. No re-inicia sesión
 * en caliente a propósito: hacerlo enmascararía la verificación de P-06
 * («tras la salida no queda rastro de auth») en los propios builds debug.
 *
 * TODO(RF-15): eliminar esta clase cuando exista el inicio de sesión real.
 */
class DebugAuthSessionSeeder @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AppInitializer {
    override suspend fun initialize() {
        if (firebaseAuth.currentUser != null) return
        try {
            signIn()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (signInError: Exception) {
            // Primera vez en este proyecto: la cuenta no existe todavía.
            runCatching { createUser() }
        }
    }

    private suspend fun signIn() {
        suspendCancellableCoroutine { continuation ->
            firebaseAuth.signInWithEmailAndPassword(EMAIL, PASSWORD)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    private suspend fun createUser() {
        suspendCancellableCoroutine { continuation ->
            firebaseAuth.createUserWithEmailAndPassword(EMAIL, PASSWORD)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    private companion object {
        // Cuenta exclusiva del entorno de prueba (proyecto aurora-31c37).
        const val EMAIL = "pruebas.debug@auroraclima.dev"
        const val PASSWORD = "nubes-de-prueba-2026"
    }
}
