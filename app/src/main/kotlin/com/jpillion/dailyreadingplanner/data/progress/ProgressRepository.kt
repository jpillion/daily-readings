package com.jpillion.dailyreadingplanner.data.progress

import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Mutable "marked read" store, keyed by the full calendar date (ESpec §5.3).
 *
 * D-ALT-12 (Alt Sprint B): every method is scoped to a `planId` so each plan keeps its own marks.
 * The parameter DEFAULTS to [PlanRegistry.DEFAULT_PLAN_ID] (the flagship), so every existing caller
 * reads exactly the Bible-Companion marks it did before this change — the parity invariant
 * (ESpec-alt §1.1). Sprint C/D thread the live active plan id through; this sprint is the storage
 * spine + the migration only, no UI change.
 *
 * D-ALT-5 (Alt Sprint C): the `Stream` enum is retired — a stream is now its plain `Int` number
 * (1..N), the value already stored in the `stream` column. Adding `planId` and switching to `Int`
 * are both strictly additive: the persisted data is identical, only the Kotlin type changed.
 */
interface ProgressRepository {
    /** Which of the day's stream NUMBERS are marked read on this exact date (year included), in [planId]. */
    fun streamsRead(
        date: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Set<Int>>

    /**
     * Days in [start]..[end] (inclusive) that have at least one mark in [planId], mapped to how
     * many streams are marked. Days with zero marks are absent. Backs the date-picker month
     * indicators (S8-T2) via a single grouped query.
     */
    fun readCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Map<LocalDate, Int>>

    /**
     * Per-day mark counts over every stored mark in [planId], all years (S11): the streak walk's
     * input. Days with zero marks are absent. Re-emits whenever marks change.
     */
    fun allReadCounts(planId: String = PlanRegistry.DEFAULT_PLAN_ID): Flow<Map<LocalDate, Int>>

    /**
     * How many days in [start]..[end] (inclusive) each stream NUMBER is marked read on in [planId]
     * (S11): backs the per-stream stat rows and, summed, the year-progress total. Streams with zero
     * marks are absent. Re-emits whenever marks change.
     */
    fun streamCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Map<Int, Int>>

    /**
     * Every marked (stream, day) in [start]..[end] (inclusive) for [planId], as the set of marked
     * dates per stream NUMBER (S17): the year-strip input. Streams with zero marks are absent.
     * Re-emits whenever marks change.
     */
    fun streamMarks(
        start: LocalDate,
        end: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Map<Int, Set<LocalDate>>>

    /**
     * True iff any reading mark exists for any date IN ANY PLAN. Used only by the one-time
     * tracking-start first-run default (S10, D-S10-1): an install with existing history must not be
     * retroactively defaulted. D-ALT-15: this stays GLOBAL, not per-plan — it is a per-device
     * "fresh install" signal, not a plan concept.
     */
    suspend fun hasAnyMarks(): Boolean

    suspend fun setRead(
        date: LocalDate,
        streamNumber: Int,
        isRead: Boolean,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )

    /**
     * Marks or unmarks the given [streamNumbers] (the active plan's `1..N`) for [date] atomically,
     * in [planId]. The caller supplies the active plan's stream numbers — the store does not know N
     * (D-ALT-5/11). An unmark deletes every mark for the day regardless of [streamNumbers].
     */
    suspend fun setWholeDay(
        date: LocalDate,
        streamNumbers: List<Int>,
        isRead: Boolean,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )

    /** Deletes every mark in [planId] dated within [year]; other years/plans are untouched (S8). */
    suspend fun clearYear(
        year: Int,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )
}
