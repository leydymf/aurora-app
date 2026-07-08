package com.auroraclima.app.weather.data

import com.auroraclima.app.weather.data.local.SelectedCityEntity
import com.auroraclima.app.weather.data.local.WeatherDao
import com.auroraclima.app.weather.data.local.WeatherSnapshotEntity
import com.auroraclima.app.weather.data.remote.CurrentWeatherDto
import com.auroraclima.app.weather.data.remote.DailyWeatherDto
import com.auroraclima.app.weather.data.remote.ForecastApi
import com.auroraclima.app.weather.data.remote.ForecastResponseDto
import com.auroraclima.app.weather.data.remote.GeocodingApi
import com.auroraclima.app.weather.data.remote.GeocodingResponseDto
import com.auroraclima.app.weather.data.remote.GeocodingResultDto
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del repositorio de clima con respuestas simuladas (RF-02, RF-03). */
class WeatherRepositoryImplTest {
    private val geocodingApi = FakeGeocodingApi()
    private val forecastApi = FakeForecastApi()
    private val dao = FakeWeatherDao()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MILLIS), ZoneOffset.UTC)
    private val repository = WeatherRepositoryImpl(geocodingApi, forecastApi, dao, fixedClock)

    @Test
    fun `la busqueda mapea los resultados de la API al dominio`() = runTest {
        geocodingApi.response = GeocodingResponseDto(
            results = listOf(
                GeocodingResultDto(
                    id = 3688465,
                    name = "Bucaramanga",
                    latitude = 7.12539,
                    longitude = -73.1198,
                    country = "Colombia",
                    admin1 = "Santander"
                )
            )
        )

        val result = repository.searchCities("Bucara")

        val cities = result.getOrThrow()
        assertEquals(1, cities.size)
        assertEquals(
            City(
                id = 3688465,
                name = "Bucaramanga",
                region = "Santander",
                country = "Colombia",
                latitude = 7.12539,
                longitude = -73.1198
            ),
            cities.first()
        )
    }

    @Test
    fun `una busqueda sin coincidencias devuelve lista vacia`() = runTest {
        geocodingApi.response = GeocodingResponseDto(results = emptyList())

        val result = repository.searchCities("xyzabc")

        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `un fallo de red en la busqueda devuelve Result de error sin excepcion`() = runTest {
        geocodingApi.failure = IOException("sin red")

        val result = repository.searchCities("Bucara")

        assertTrue(result.isFailure)
    }

    @Test
    fun `refrescar el clima mapea el pronostico y lo guarda en cache`() = runTest {
        forecastApi.response = sampleForecast()

        val result = repository.refreshSnapshot(bucaramanga())

        val snapshot = result.getOrThrow()
        assertEquals("Bucaramanga", snapshot.cityName)
        assertEquals(24.3, snapshot.temperatureC, 0.001)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, snapshot.condition)
        assertEquals(28.1, snapshot.maxTemperatureC, 0.001)
        assertEquals(19.4, snapshot.minTemperatureC, 0.001)
        assertEquals(76, snapshot.humidityPercent)
        assertEquals(9.2, snapshot.windSpeedKmh, 0.001)
        assertEquals(55, snapshot.rainProbabilityPercent)
        assertEquals(FIXED_NOW_MILLIS, snapshot.updatedAtEpochMillis)
        // La cache queda lista para consultas sin conexión (RF-03.2)
        assertEquals(snapshot, repository.getCachedSnapshot())
    }

    @Test
    fun `un fallo de red al refrescar devuelve error y conserva la cache previa`() = runTest {
        forecastApi.response = sampleForecast()
        repository.refreshSnapshot(bucaramanga())
        forecastApi.failure = IOException("sin red")

        val result = repository.refreshSnapshot(bucaramanga())

        assertTrue(result.isFailure)
        val cached = repository.getCachedSnapshot()
        assertEquals("Bucaramanga", cached?.cityName)
        assertEquals(FIXED_NOW_MILLIS, cached?.updatedAtEpochMillis)
    }

    @Test
    fun `la ciudad predeterminada persiste y se recupera`() = runTest {
        val city = bucaramanga()

        repository.saveDefaultCity(city)

        assertEquals(city, repository.getDefaultCity())
    }

    @Test
    fun `sin ciudad guardada no hay predeterminada ni cache`() = runTest {
        assertNull(repository.getDefaultCity())
        assertNull(repository.getCachedSnapshot())
    }

    private fun bucaramanga() = City(
        id = 3688465,
        name = "Bucaramanga",
        region = "Santander",
        country = "Colombia",
        latitude = 7.12539,
        longitude = -73.1198
    )

    private fun sampleForecast() = ForecastResponseDto(
        current = CurrentWeatherDto(
            temperatureC = 24.3,
            humidityPercent = 76,
            weatherCode = 2,
            windSpeedKmh = 9.2
        ),
        daily = DailyWeatherDto(
            maxTemperaturesC = listOf(28.1),
            minTemperaturesC = listOf(19.4),
            rainProbabilitiesPercent = listOf(55)
        )
    )

    companion object {
        private const val FIXED_NOW_MILLIS = 1_783_500_000_000L
    }
}

private class FakeGeocodingApi : GeocodingApi {
    var response = GeocodingResponseDto()
    var failure: Exception? = null

    override suspend fun searchCities(
        name: String,
        count: Int,
        language: String,
        format: String
    ): GeocodingResponseDto {
        failure?.let { throw it }
        return response
    }
}

private class FakeForecastApi : ForecastApi {
    lateinit var response: ForecastResponseDto
    var failure: Exception? = null

    override suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        current: String,
        daily: String,
        forecastDays: Int,
        timezone: String
    ): ForecastResponseDto {
        failure?.let { throw it }
        return response
    }
}

private class FakeWeatherDao : WeatherDao {
    private var city: SelectedCityEntity? = null
    private var snapshot: WeatherSnapshotEntity? = null

    override suspend fun getSelectedCity(): SelectedCityEntity? = city

    override suspend fun saveSelectedCity(city: SelectedCityEntity) {
        this.city = city
    }

    override suspend fun getSnapshot(): WeatherSnapshotEntity? = snapshot

    override suspend fun saveSnapshot(snapshot: WeatherSnapshotEntity) {
        this.snapshot = snapshot
    }
}
