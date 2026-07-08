package com.auroraclima.app.weather.data

import com.auroraclima.app.weather.data.local.SelectedCityEntity
import com.auroraclima.app.weather.data.local.WeatherDao
import com.auroraclima.app.weather.data.local.WeatherSnapshotEntity
import com.auroraclima.app.weather.data.remote.ForecastApi
import com.auroraclima.app.weather.data.remote.ForecastResponseDto
import com.auroraclima.app.weather.data.remote.GeocodingApi
import com.auroraclima.app.weather.data.remote.GeocodingResultDto
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition
import com.auroraclima.app.weather.domain.model.WeatherSnapshot
import com.auroraclima.app.weather.domain.repository.WeatherRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val forecastApi: ForecastApi,
    private val weatherDao: WeatherDao,
    private val clock: Clock
) : WeatherRepository {
    override suspend fun searchCities(query: String): Result<List<City>> = suspendRunCatching {
        geocodingApi.searchCities(name = query).results.map { it.toDomain() }
    }

    override suspend fun getDefaultCity(): City? = weatherDao.getSelectedCity()?.toDomain()

    override suspend fun saveDefaultCity(city: City) {
        weatherDao.saveSelectedCity(city.toEntity())
    }

    override suspend fun getCachedSnapshot(): WeatherSnapshot? = weatherDao.getSnapshot()?.toDomain()

    override suspend fun refreshSnapshot(city: City): Result<WeatherSnapshot> = suspendRunCatching {
        val snapshot = forecastApi
            .getCurrentWeather(latitude = city.latitude, longitude = city.longitude)
            .toDomain(cityName = city.name, updatedAtEpochMillis = clock.millis())
        weatherDao.saveSnapshot(snapshot.toEntity())
        snapshot
    }
}

/** Como runCatching, pero relanza la cancelación para no romper la concurrencia estructurada. */
private inline fun <T> suspendRunCatching(block: () -> T): Result<T> = runCatching(block)
    .onFailure { if (it is CancellationException) throw it }

private fun GeocodingResultDto.toDomain() = City(
    id = id,
    name = name,
    region = admin1,
    country = country,
    latitude = latitude,
    longitude = longitude
)

private fun City.toEntity() = SelectedCityEntity(
    cityId = id,
    name = name,
    region = region,
    country = country,
    latitude = latitude,
    longitude = longitude
)

private fun SelectedCityEntity.toDomain() = City(
    id = cityId,
    name = name,
    region = region,
    country = country,
    latitude = latitude,
    longitude = longitude
)

private fun ForecastResponseDto.toDomain(cityName: String, updatedAtEpochMillis: Long) = WeatherSnapshot(
    cityName = cityName,
    temperatureC = current.temperatureC,
    condition = WeatherCondition.fromWmoCode(current.weatherCode),
    maxTemperatureC = daily.maxTemperaturesC.first(),
    minTemperatureC = daily.minTemperaturesC.first(),
    humidityPercent = current.humidityPercent,
    windSpeedKmh = current.windSpeedKmh,
    rainProbabilityPercent = daily.rainProbabilitiesPercent.firstOrNull() ?: 0,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun WeatherSnapshot.toEntity() = WeatherSnapshotEntity(
    cityName = cityName,
    temperatureC = temperatureC,
    weatherCode = condition.toStorageCode(),
    maxTemperatureC = maxTemperatureC,
    minTemperatureC = minTemperatureC,
    humidityPercent = humidityPercent,
    windSpeedKmh = windSpeedKmh,
    rainProbabilityPercent = rainProbabilityPercent,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun WeatherSnapshotEntity.toDomain() = WeatherSnapshot(
    cityName = cityName,
    temperatureC = temperatureC,
    condition = WeatherCondition.fromWmoCode(weatherCode),
    maxTemperatureC = maxTemperatureC,
    minTemperatureC = minTemperatureC,
    humidityPercent = humidityPercent,
    windSpeedKmh = windSpeedKmh,
    rainProbabilityPercent = rainProbabilityPercent,
    updatedAtEpochMillis = updatedAtEpochMillis
)

/** Código WMO representativo por condición, para persistir sin acoplar la BD al enum. */
private fun WeatherCondition.toStorageCode(): Int = when (this) {
    WeatherCondition.CLEAR -> 0
    WeatherCondition.PARTLY_CLOUDY -> 2
    WeatherCondition.OVERCAST -> 3
    WeatherCondition.FOG -> 45
    WeatherCondition.DRIZZLE -> 51
    WeatherCondition.RAIN -> 61
    WeatherCondition.SNOW -> 71
    WeatherCondition.THUNDERSTORM -> 95
}
