package com.auroraclima.app.weather.data.di

import android.content.Context
import androidx.room.Room
import com.auroraclima.app.core.network.RetrofitFactory
import com.auroraclima.app.weather.data.WeatherRepositoryImpl
import com.auroraclima.app.weather.data.local.WeatherDao
import com.auroraclima.app.weather.data.local.WeatherDatabase
import com.auroraclima.app.weather.data.remote.ForecastApi
import com.auroraclima.app.weather.data.remote.GeocodingApi
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherDataModule {
    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    companion object {
        @Provides
        @Singleton
        fun provideGeocodingApi(factory: RetrofitFactory): GeocodingApi =
            factory.create(GeocodingApi.BASE_URL).create(GeocodingApi::class.java)

        @Provides
        @Singleton
        fun provideForecastApi(factory: RetrofitFactory): ForecastApi =
            factory.create(ForecastApi.BASE_URL).create(ForecastApi::class.java)

        @Provides
        @Singleton
        fun provideWeatherDatabase(@ApplicationContext context: Context): WeatherDatabase = Room
            .databaseBuilder(context, WeatherDatabase::class.java, WeatherDatabase.NAME)
            .build()

        @Provides
        fun provideWeatherDao(database: WeatherDatabase): WeatherDao = database.weatherDao()
    }
}
