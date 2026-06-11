package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingProgressEntity::class],
    version = 1,
    exportSchema = false, // no migrations until the V2 streak schema; revisit with the Room Gradle plugin then
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
}
