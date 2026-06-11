package com.jpillion.dailyreadingplanner.data.progress

import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Mutable "marked read" store, keyed by the full calendar date (ESpec §5.3). */
interface ProgressRepository {
    /** Which of the day's streams are marked read on this exact date (year included). */
    fun streamsRead(date: LocalDate): Flow<Set<Stream>>

    /**
     * Days in [start]..[end] (inclusive) that have at least one mark, mapped to how many
     * streams are marked. Days with zero marks are absent. Backs the date-picker month
     * indicators (S8-T2) via a single grouped query.
     */
    fun readCounts(
        start: LocalDate,
        end: LocalDate,
    ): Flow<Map<LocalDate, Int>>

    /**
     * Per-day mark counts over every stored mark, all years (S11): the streak walk's input.
     * Days with zero marks are absent. Re-emits whenever marks change.
     */
    fun allReadCounts(): Flow<Map<LocalDate, Int>>

    /**
     * How many days in [start]..[end] (inclusive) each stream is marked read on (S11):
     * backs the per-stream stat rows and, summed, the year-progress total. Streams with
     * zero marks are absent. Re-emits whenever marks change.
     */
    fun streamCounts(
        start: LocalDate,
        end: LocalDate,
    ): Flow<Map<Stream, Int>>

    /**
     * True iff any reading mark exists for any date. Used only by the one-time
     * tracking-start first-run default (S10, D-S10-1): an install with existing history
     * must not be retroactively defaulted.
     */
    suspend fun hasAnyMarks(): Boolean

    suspend fun setRead(
        date: LocalDate,
        stream: Stream,
        isRead: Boolean,
    )

    /** Marks or unmarks all three streams for [date] atomically. */
    suspend fun setWholeDay(
        date: LocalDate,
        isRead: Boolean,
    )

    /** Deletes every mark dated within [year]; other years are untouched (owner decision, S8). */
    suspend fun clearYear(year: Int)
}
