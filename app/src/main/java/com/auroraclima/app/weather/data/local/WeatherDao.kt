package com.auroraclima.app.weather.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
}
