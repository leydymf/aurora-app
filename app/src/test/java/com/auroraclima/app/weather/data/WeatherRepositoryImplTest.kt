package com.auroraclima.app.weather.data

import com.auroraclima.app.weather.data.local.DailyForecastEntity
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
import java.time.LocalDate
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
    fun `refrescar el clima guarda el pronostico de 5 dias en orden`() = runTest {
        // RF-04 criterio 1: al menos 5 días desde la vista de la ciudad
        forecastApi.response = sampleForecast()

        repository.refreshSnapshot(bucaramanga())

        val forecast = repository.getCachedForecast()
        assertEquals(5, forecast.size)
        val first = forecast.first()
        assertEquals(LocalDate.of(2026, 7, 6), first.date)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, first.condition)
        assertEquals(28.1, first.maxTemperatureC, 0.001)
        assertEquals(19.4, first.minTemperatureC, 0.001)
        assertEquals(55, first.rainProbabilityPercent)
        val last = forecast.last()
        assertEquals(LocalDate.of(2026, 7, 10), last.date)
        assertEquals(WeatherCondition.CLEAR, last.condition)
        assertEquals(20, last.rainProbabilityPercent)
    }

    @Test
    fun `un dia con datos incompletos se omite sin invalidar el resto`() = runTest {
        // Parsing tolerante: fecha ilegible o valor faltante no rompe el listado
        val base = sampleForecast()
        forecastApi.response = base.copy(
            daily = base.daily.copy(
                dates = listOf("2026-07-06", "fecha-invalida", "2026-07-08", "2026-07-09", "2026-07-10"),
                // el quinto día no llegó
                maxTemperaturesC = listOf(28.1, 26.0, 25.2, 27.3)
            )
        )

        repository.refreshSnapshot(bucaramanga())

        val forecast = repository.getCachedForecast()
        assertEquals(
            listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 9)),
            forecast.map { it.date }
        )
    }

    @Test
    fun `un dia sin probabilidad de lluvia se guarda como cero`() = runTest {
        val base = sampleForecast()
        forecastApi.response = base.copy(
            daily = base.daily.copy(rainProbabilitiesPercent = listOf(55, null, 70, 40, 20))
        )

        repository.refreshSnapshot(bucaramanga())

        assertEquals(0, repository.getCachedForecast()[1].rainProbabilityPercent)
    }

    @Test
    fun `cada refresco reemplaza el pronostico anterior completo`() = runTest {
        forecastApi.response = sampleForecast()
        repository.refreshSnapshot(bucaramanga())
        val base = sampleForecast()
        forecastApi.response = base.copy(
            daily = base.daily.copy(
                dates = listOf("2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11")
            )
        )

        repository.refreshSnapshot(bucaramanga())

        val forecast = repository.getCachedForecast()
        assertEquals(5, forecast.size)
        assertEquals(LocalDate.of(2026, 7, 7), forecast.first().date)
    }

    @Test
    fun `sin refresco previo el pronostico en cache esta vacio`() = runTest {
        assertTrue(repository.getCachedForecast().isEmpty())
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
            dates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10"),
            weatherCodes = listOf(2, 61, 61, 2, 0),
            maxTemperaturesC = listOf(28.1, 26.0, 25.2, 27.3, 29.0),
            minTemperaturesC = listOf(19.4, 18.2, 18.0, 19.1, 19.3),
            rainProbabilitiesPercent = listOf(55, 80, 70, 40, 20)
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

    override suspend fun getForecast(
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
    private var forecast: List<DailyForecastEntity> = emptyList()

    override suspend fun getSelectedCity(): SelectedCityEntity? = city

    override suspend fun saveSelectedCity(city: SelectedCityEntity) {
        this.city = city
    }

    override suspend fun getSnapshot(): WeatherSnapshotEntity? = snapshot

    override suspend fun saveSnapshot(snapshot: WeatherSnapshotEntity) {
        this.snapshot = snapshot
    }

    override suspend fun getForecast(): List<DailyForecastEntity> = forecast.sortedBy { it.dayIndex }

    override suspend fun clearForecast() {
        forecast = emptyList()
    }

    override suspend fun insertForecast(days: List<DailyForecastEntity>) {
        forecast = forecast + days
    }
}
