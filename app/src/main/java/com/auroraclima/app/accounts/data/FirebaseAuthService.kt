package com.auroraclima.app.accounts.data

import com.auroraclima.app.core.network.awaitCompletion
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Envoltorio fino sobre Firebase Auth: único lugar que conoce las clases del SDK.
 * El puente Task→coroutine es el compartido de `core/network` (awaitCompletion),
 * hecho a mano para no sumar la dependencia kotlinx-coroutines-play-services
 * (criterios A2-1); convergió aquí a la tercera repetición, como P-39.
 */
@Singleton
class FirebaseAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthService {
    override suspend fun createUser(email: String, password: String) {
        try {
            firebaseAuth.createUserWithEmailAndPassword(email, password).awaitCompletion()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FirebaseAuthException) {
            throw AuthServiceException(error.errorCode, error)
        }
    }

    override suspend fun signIn(email: String, password: String) {
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password).awaitCompletion()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FirebaseAuthException) {
            throw AuthServiceException(error.errorCode, error)
        }
    }

    override fun hasActiveSession(): Boolean = firebaseAuth.currentUser != null

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun reauthenticate(password: String) {
        translatingFailures { user ->
            val email = user.email ?: throw AuthServiceException(null)
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).awaitCompletion()
        }
    }

    override suspend fun deleteCurrentUser() {
        translatingFailures { user -> user.delete().awaitCompletion() }
    }

    /**
     * Único punto que traduce los fallos del proveedor para las operaciones de
     * RF-20. La caída de red llega como excepción propia (no [FirebaseAuthException])
     * y aquí se le da código: el flujo de eliminación debe poder explicar que sin
     * conexión NADA se ha borrado, sin dejar que una clase del SDK cruce la capa.
     */
    private suspend fun translatingFailures(block: suspend (FirebaseUser) -> Unit) {
        val user = firebaseAuth.currentUser ?: throw AuthServiceException(null)
        try {
            block(user)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FirebaseAuthException) {
            throw AuthServiceException(error.errorCode, error)
        } catch (error: FirebaseNetworkException) {
            throw AuthServiceException(AuthServiceException.ERROR_NETWORK, error)
        }
    }
}
