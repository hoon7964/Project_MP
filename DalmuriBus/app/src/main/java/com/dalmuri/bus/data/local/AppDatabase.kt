package com.dalmuri.bus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** 앱 안 DB 파일 dalmuri.db. 테이블: stops, map_info */
@Database(entities = [StopEntity::class, MapInfoEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun mapInfoDao(): MapInfoDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dalmuri.db").build()
    }
}
