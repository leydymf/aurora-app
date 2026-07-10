package com.auroraclima.app.accounts.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Envoltorio fino sobre Firebase Auth: único lugar que conoce las clases del SDK.
 * El puente Task→coroutine se hace a mano para no sumar la dependencia
 * kotlinx-coroutines-play-services (criterios A2-1).
 */
@Singleton
class FirebaseAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthService {
    override suspend fun createUser(email: String, password: String) {
        try {
            awaitCreateUser(email, password)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FirebaseAuthException) {
            throw AuthServiceException(error.errorCode, error)
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend fun awaitCreateUser(email: String, password: String) {
        suspendCancellableCoroutine { continuation ->
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }
}
