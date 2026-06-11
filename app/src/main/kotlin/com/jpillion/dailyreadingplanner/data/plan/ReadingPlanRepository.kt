package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.domain.model.Portion

/** Read-only access to the date-anchored 365-day schedule (ESpec §2). */
interface ReadingPlanRepository {
    /** The exactly-3 portions scheduled for [date]. The plan covers every valid [ReadingDate]. */
    suspend fun portionsFor(date: ReadingDate): List<Portion>
}
