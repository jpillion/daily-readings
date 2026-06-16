package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.Portion

/**
 * Read-only access to a plan's date-anchored 365-day schedule (ESpec-alt §3.5). Now per-plan:
 * every method names a [planId] (the registry id). A plan's asset is parsed on first touch and
 * memoized per-plan, so the default plan's cold-start cost is unchanged (M2).
 */
interface ReadingPlanRepository {
    /** The portions scheduled for [date] under [planId] (one per declared stream). */
    suspend fun portionsFor(
        planId: String,
        date: ReadingDate,
    ): List<Portion>

    /** [planId]'s self-declared shape (stream count + titles, dayCount, anchoring). */
    suspend fun descriptor(planId: String): PlanDescriptor
}
