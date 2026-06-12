package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row of the grouped range query: a day and how many of its streams are marked. */
data class DayReadCount(
    val dateEpochDay: Long,
    val readCount: Int,
)

/** One row of the raw marks-in-range query (S17): a single (day, stream) mark. */
data class DayStreamMark(
    val dateEpochDay: Long,
    val stream: Int,
)

/** One row of the per-stream grouped query: a stream and how many of its days are marked. */
data class StreamReadCount(
    val stream: Int,
    val readCount: Int,
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT stream FROM reading_progress WHERE dateEpochDay = :dateEpochDay")
    fun streamsRead(dateEpochDay: Long): Flow<List<Int>>

    /**
     * Per-day mark counts over an inclusive range, in ONE grouped query — the seam the
     * date-picker month indicators read through (S8-T1); never loop per-day from the UI.
     */
    @Query(
        "SELECT dateEpochDay, COUNT(stream) AS readCount FROM reading_progress " +
            "WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay GROUP BY dateEpochDay",
    )
    fun readCountsInRange(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<DayReadCount>>

    /**
     * Per-day mark counts over ALL stored marks, in one grouped query — the streak walk's
     * input (S11, R-STREAK-4: longest streak is all-time, so no year bound). The table holds
     * at most ~1,095 rows per tracked year; loading every grouped row is trivially cheap.
     */
    @Query("SELECT dateEpochDay, COUNT(stream) AS readCount FROM reading_progress GROUP BY dateEpochDay")
    fun allReadCounts(): Flow<List<DayReadCount>>

    /**
     * Per-stream mark counts over an inclusive range, in one grouped query — backs both the
     * year-progress total (as the sum) and the three per-stream stat rows (S11).
     */
    @Query(
        "SELECT stream, COUNT(*) AS readCount FROM reading_progress " +
            "WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay GROUP BY stream",
    )
    fun streamCountsInRange(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<StreamReadCount>>

    /**
     * Every individual (day, stream) mark in an inclusive range, in one query — the year-strip
     * input (S17): at most ~1,098 rows for a fully-read year, trivially cheap.
     */
    @Query(
        "SELECT dateEpochDay, stream FROM reading_progress " +
            "WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay",
    )
    fun marksInRange(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<DayStreamMark>>

    /** True iff any mark exists at all — backs the one-time tracking-start default (S10). */
    @Query("SELECT EXISTS(SELECT 1 FROM reading_progress LIMIT 1)")
    suspend fun hasAnyRows(): Boolean

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

    /** Inclusive ranged delete backing the year-scoped "Reset progress" (S8-T1). */
    @Query("DELETE FROM reading_progress WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun deleteRange(
        startEpochDay: Long,
        endEpochDay: Long,
    )
}
