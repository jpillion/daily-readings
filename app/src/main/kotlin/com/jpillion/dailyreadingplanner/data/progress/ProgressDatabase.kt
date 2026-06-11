package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingProgressEntity::class],
    version = 1,
    // D-S9-4: schema checked in at app/schemas — the pinned baseline V2 migrations start from.
    exportSchema = true,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
}
