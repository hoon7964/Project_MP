package com.dalmuri.bus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StopDao {
    @Query("SELECT * FROM stops")
    suspend fun getAll(): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)
}

@Dao
interface MapInfoDao {
    @Query("SELECT * FROM map_info LIMIT 1")
    suspend fun get(): MapInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: MapInfoEntity)
}
