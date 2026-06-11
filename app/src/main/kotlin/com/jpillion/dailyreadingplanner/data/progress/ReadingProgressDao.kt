package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT stream FROM reading_progress WHERE dateEpochDay = :dateEpochDay")
    fun streamsRead(dateEpochDay: Long): Flow<List<Int>>

    /** Insert-or-replace; a multi-row call is a single transaction (whole-day mark atomicity). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ReadingProgressEntity>)

    @Query("DELETE FROM reading_progress WHERE dateEpochDay = :dateEpochDay AND stream = :stream")
    suspend fun delete(
        dateEpochDay: Long,
        stream: Int,
    )

    @Query("DELETE FROM reading_progress WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteDay(dateEpochDay: Long)
}
