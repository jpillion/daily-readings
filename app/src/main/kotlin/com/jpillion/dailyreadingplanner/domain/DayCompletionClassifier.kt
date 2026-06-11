package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import java.time.LocalDate
import javax.inject.Inject

/**
 * THE single day-classification truth table (S10 predicate, extracted in S11 per D-S11-1):
 * both the date-picker month indicators ([GetMonthCompletionUseCase]) and the streak/stats
 * derivation ([GetReadingStatsUseCase]) consume this — never re-derive missed-ness
 * (R-STREAK-5: one source of truth, no drift between the picker dots and the stats screen).
 *
 * Classification (owner spec + S10 truth table, docs/features/tracking-start-date.md §2,
 * evaluated in order): Feb 29 is always NONE (D1); all three streams read = COMPLETE for any
 * day (pre-start days keep their earned green); a day strictly before the tracking start date
 * is NONE, never MISSED; a past scheduled in-tracking day short of three = MISSED; an
 * incomplete today/future day = NONE.
 */
class DayCompletionClassifier
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
    ) {
        fun classify(
            date: LocalDate,
            readCount: Int,
            today: LocalDate,
            trackingStart: LocalDate?,
        ): DayCompletion =
            when {
                resolver.resolve(date) is ResolvedDate.NoScheduledReadings -> DayCompletion.NONE
                readCount >= STREAM_COUNT -> DayCompletion.COMPLETE
                // Start-date gate (S10): strictly-before-start days are neutral, never MISSED.
                // Sits AFTER the COMPLETE branch (earned green is kept) and immediately BEFORE
                // the MISSED branch (it only ever suppresses red, never adds it).
                trackingStart != null && date.isBefore(trackingStart) -> DayCompletion.NONE
                date.isBefore(today) -> DayCompletion.MISSED
                else -> DayCompletion.NONE
            }

        companion object {
            const val STREAM_COUNT = 3
        }
    }
