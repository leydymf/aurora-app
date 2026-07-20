package com.auroraclima.app.core.network

import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Instancias únicas del backend de Firebase, compartidas por varios features
 * (RNF-06, §2: viven en core para que ningún feature dependa de otro). Firestore
 * lleva caché local persistente explícita: lo ya consultado sigue disponible sin
 * conexión (RF-25, RF-26, RNF-08) y las escrituras hechas sin red quedan en su
 * cola durable hasta reconectar (RF-37/RF-38 — la persistencia del SDK ES la
 * cola offline→online). Firebase Auth da el `uid` con el que la sync restringe
 * el acceso a la cuenta de la usuaria (RF-37 criterio 2).
 */
@Module
@InstallIn(SingletonComponent::class)
object FirestoreModule {
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** La caché persistente del SDK muere con la cuenta (RF-20, riesgo P-22). */
    @Provides
    @IntoSet
    fun provideFirestoreCacheEraser(impl: FirestoreCacheEraser): LocalAccountDataEraser = impl
}
