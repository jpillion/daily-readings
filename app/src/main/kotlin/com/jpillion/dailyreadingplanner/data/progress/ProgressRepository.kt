package com.jpillion.dailyreadingplanner.data.progress

import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import com.jpillion.dailyreadingplanner.domain.model.Stream
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
 * NOTE: this interface still speaks the [Stream] enum — retiring it is Sprint C (D-ALT-5). Adding
 * `planId` is strictly additive.
 */
interface ProgressRepository {
    /** Which of the day's streams are marked read on this exact date (year included), in [planId]. */
    fun streamsRead(
        date: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Set<Stream>>

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
     * How many days in [start]..[end] (inclusive) each stream is marked read on in [planId] (S11):
     * backs the per-stream stat rows and, summed, the year-progress total. Streams with zero marks
     * are absent. Re-emits whenever marks change.
     */
    fun streamCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Map<Stream, Int>>

    /**
     * Every marked (stream, day) in [start]..[end] (inclusive) for [planId], as the set of marked
     * dates per stream (S17): the year-strip input. Streams with zero marks are absent. Re-emits
     * whenever marks change.
     */
    fun streamMarks(
        start: LocalDate,
        end: LocalDate,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    ): Flow<Map<Stream, Set<LocalDate>>>

    /**
     * True iff any reading mark exists for any date IN ANY PLAN. Used only by the one-time
     * tracking-start first-run default (S10, D-S10-1): an install with existing history must not be
     * retroactively defaulted. D-ALT-15: this stays GLOBAL, not per-plan — it is a per-device
     * "fresh install" signal, not a plan concept.
     */
    suspend fun hasAnyMarks(): Boolean

    suspend fun setRead(
        date: LocalDate,
        stream: Stream,
        isRead: Boolean,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )

    /** Marks or unmarks all of the plan's three streams for [date] atomically, in [planId]. */
    suspend fun setWholeDay(
        date: LocalDate,
        isRead: Boolean,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )

    /** Deletes every mark in [planId] dated within [year]; other years/plans are untouched (S8). */
    suspend fun clearYear(
        year: Int,
        planId: String = PlanRegistry.DEFAULT_PLAN_ID,
    )
}
