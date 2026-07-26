package com.auroraclima.app.core.network

import com.auroraclima.app.core.security.KeyEnvelopeRemoteSource
import com.auroraclima.app.core.security.PublishedKeyEnvelope
import com.auroraclima.app.core.security.WrappedKeyEnvelope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Documento de llaves del respaldo en Firestore (RF-39): `/users/{uid}/meta/profile`,
 * cubierto por las mismas reglas que `entries` (solo la dueña, RNF-06). Campos
 * neutros de una letra (RNF-05): nada en el documento ni en la caché local del
 * SDK delata que se trata de material de llaves.
 *
 * A diferencia de la sync de mejor esfuerzo (RF-37), aquí TODO espera al
 * servidor: `fetch` decide sobre el estado real (no la caché — un sobre viejo
 * re-publicado desharía una rotación) y `publish` no puede darse por hecho con
 * un encolado local (dos dispositivos decidirían sobre generaciones distintas).
 */
@Singleton
class FirestoreKeyEnvelopeRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : KeyEnvelopeRemoteSource {
    override suspend fun fetch(): PublishedKeyEnvelope? {
        val snapshot = document().get(Source.SERVER).awaitCompletion()
        if (!snapshot.exists()) return null
        return PublishedKeyEnvelope(
            envelope = WrappedKeyEnvelope(
                ciphertext = decode(snapshot.getString(FIELD_KEY) ?: return null),
                nonce = decode(snapshot.getString(FIELD_NONCE) ?: return null),
                salt = decode(snapshot.getString(FIELD_SALT) ?: return null),
                iterations = (snapshot.getLong(FIELD_PARAMS) ?: return null).toInt()
            ),
            generation = snapshot.getLong(FIELD_GENERATION) ?: return null,
            revision = snapshot.getString(FIELD_REVISION) ?: return null,
            schemaVersion = snapshot.getLong(FIELD_SCHEMA_VERSION) ?: return null
        )
    }

    override suspend fun publish(envelope: PublishedKeyEnvelope) {
        document().set(
            mapOf(
                FIELD_KEY to encode(envelope.envelope.ciphertext),
                FIELD_NONCE to encode(envelope.envelope.nonce),
                FIELD_SALT to encode(envelope.envelope.salt),
                FIELD_PARAMS to envelope.envelope.iterations.toLong(),
                FIELD_GENERATION to envelope.generation,
                FIELD_REVISION to envelope.revision,
                FIELD_SCHEMA_VERSION to envelope.schemaVersion
            )
        ).awaitCompletion()
    }

    override suspend fun delete() {
        document().delete().awaitCompletion()
    }

    /** `/users/{uid}/meta/profile` de la usuaria autenticada. */
    private fun document() = firestore.collection(USERS)
        .document(requireUid())
        .collection(META)
        .document(PROFILE)

    // Sin sesión no hay documento; excepción neutra y sin mensaje (regla 4).
    private fun requireUid(): String = firebaseAuth.currentUser?.uid ?: throw IllegalStateException()

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        // Nombres neutros: viajan en rutas del SDK y quedan en la caché (RNF-05).
        const val USERS = "users"
        const val META = "meta"
        const val PROFILE = "profile"
        const val FIELD_KEY = "k"
        const val FIELD_NONCE = "n"
        const val FIELD_SALT = "s"
        const val FIELD_PARAMS = "p"
        const val FIELD_GENERATION = "g"
        const val FIELD_REVISION = "r"
        const val FIELD_SCHEMA_VERSION = "v"
    }
}
