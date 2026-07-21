package com.auroraclima.app.weather.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WeatherDao {
    @Query("SELECT * FROM selected_city WHERE rowId = $SINGLE_ROW_ID")
    suspend fun getSelectedCity(): SelectedCityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSelectedCity(city: SelectedCityEntity)

    @Query("SELECT * FROM weather_snapshot WHERE rowId = $SINGLE_ROW_ID")
    suspend fun getSnapshot(): WeatherSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: WeatherSnapshotEntity)

    @Query("SELECT * FROM daily_forecast ORDER BY dayIndex")
    suspend fun getForecast(): List<DailyForecastEntity>

    /** Sustituye el pronóstico completo: nunca conviven días de dos refrescos distintos. */
    @Transaction
    suspend fun replaceForecast(days: List<DailyForecastEntity>) {
        clearForecast()
        insertForecast(days)
    }

    @Query("DELETE FROM daily_forecast")
    suspend fun clearForecast()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecast(days: List<DailyForecastEntity>)
}
