package com.jpillion.dailyreadingplanner.data.progress

import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Mutable "marked read" store, keyed by the full calendar date (ESpec §5.3). */
interface ProgressRepository {
    /** Which of the day's streams are marked read on this exact date (year included). */
    fun streamsRead(date: LocalDate): Flow<Set<Stream>>

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
}
