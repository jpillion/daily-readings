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

/**
 * Progress DAO. D-ALT-12 (Alt Sprint B): every read/write/count is scoped to a single `plan_id`
 * so each plan keeps its own marks (stats/streaks/strips/picker become per active plan by
 * construction). [hasAnyRows] is the ONLY query that stays GLOBAL (no plan filter) — it backs the
 * "is this a fresh install" tracking-start default, which is a per-device, not per-plan, concept
 * (D-ALT-15).
 */
@Dao
interface ReadingProgressDao {
    @Query("SELECT stream FROM reading_progress WHERE plan_id = :planId AND dateEpochDay = :dateEpochDay")
    fun streamsRead(
        planId: String,
        dateEpochDay: Long,
    ): Flow<List<Int>>

    /**
     * Per-day mark counts over an inclusive range, in ONE grouped query — the seam the
     * date-picker month indicators read through (S8-T1); never loop per-day from the UI.
     */
    @Query(
        "SELECT dateEpochDay, COUNT(stream) AS readCount FROM reading_progress " +
            "WHERE plan_id = :planId AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "GROUP BY dateEpochDay",
    )
    fun readCountsInRange(
        planId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<DayReadCount>>

    /**
     * Per-day mark counts over ALL stored marks for the plan, in one grouped query — the streak
     * walk's input (S11, R-STREAK-4: longest streak is all-time, so no year bound). The table holds
     * at most ~1,095 rows per tracked year per plan; loading every grouped row is trivially cheap.
     */
    @Query(
        "SELECT dateEpochDay, COUNT(stream) AS readCount FROM reading_progress " +
            "WHERE plan_id = :planId GROUP BY dateEpochDay",
    )
    fun allReadCounts(planId: String): Flow<List<DayReadCount>>

    /**
     * Per-stream mark counts over an inclusive range, in one grouped query — backs both the
     * year-progress total (as the sum) and the per-stream stat rows (S11).
     */
    @Query(
        "SELECT stream, COUNT(*) AS readCount FROM reading_progress " +
            "WHERE plan_id = :planId AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "GROUP BY stream",
    )
    fun streamCountsInRange(
        planId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<StreamReadCount>>

    /**
     * Every individual (day, stream) mark in an inclusive range, in one query — the year-strip
     * input (S17): at most ~1,098 rows for a fully-read year per plan, trivially cheap.
     */
    @Query(
        "SELECT dateEpochDay, stream FROM reading_progress " +
            "WHERE plan_id = :planId AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay",
    )
    fun marksInRange(
        planId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<DayStreamMark>>

    /**
     * True iff any mark exists AT ALL, for ANY plan — backs the one-time tracking-start default
     * (S10). D-ALT-15: this stays GLOBAL (no `plan_id` filter); a device with history under any
     * plan is not a "fresh install."
     */
    @Query("SELECT EXISTS(SELECT 1 FROM reading_progress LIMIT 1)")
    suspend fun hasAnyRows(): Boolean

    /** Insert-or-replace; a multi-row call is a single transaction (whole-day mark atomicity). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ReadingProgressEntity>)

    @Query(
        "DELETE FROM reading_progress " +
            "WHERE plan_id = :planId AND dateEpochDay = :dateEpochDay AND stream = :stream",
    )
    suspend fun delete(
        planId: String,
        dateEpochDay: Long,
        stream: Int,
    )

    @Query("DELETE FROM reading_progress WHERE plan_id = :planId AND dateEpochDay = :dateEpochDay")
    suspend fun deleteDay(
        planId: String,
        dateEpochDay: Long,
    )

    /** Inclusive ranged delete backing the year-scoped "Reset progress" (S8-T1), per plan. */
    @Query(
        "DELETE FROM reading_progress " +
            "WHERE plan_id = :planId AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay",
    )
    suspend fun deleteRange(
        planId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    )
}
