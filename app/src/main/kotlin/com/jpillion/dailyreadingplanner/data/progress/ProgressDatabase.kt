package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingProgressEntity::class],
    // D-ALT-12 (Alt Sprint B): v1 → v2 adds the `plan_id` column + makes it part of the PK.
    // The hand-written MIGRATION_1_2 (zero loss) is the only path 1 → 2; see DataModule.
    version = 2,
    // D-S9-4: schema checked in at app/schemas — the pinned baseline migrations migrate from/to.
    exportSchema = true,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
}
