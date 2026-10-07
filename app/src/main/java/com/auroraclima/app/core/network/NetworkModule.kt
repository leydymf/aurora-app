package com.auroraclima.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Configuración de red compartida. Sin interceptores de logging (regla 4 de CLAUDE.md). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideRetrofitFactory(json: Json): RetrofitFactory = RetrofitFactory(json)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** Señal de conectividad del reingreso silencioso (P-31 opción C, RNF-08). */
    @Provides
    @Singleton
    fun provideConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor = impl
}

/** Crea instancias de Retrofit con la conversión JSON compartida. */
class RetrofitFactory(private val json: Json) {
    fun create(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
