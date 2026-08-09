package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * THE single day-classification truth table (S10 predicate, extracted in S11 per D-S11-1):
 * both the date-picker month indicators ([GetMonthCompletionUseCase]) and the streak/stats
 * derivation ([GetReadingStatsUseCase]) consume this — never re-derive missed-ness
 * (R-STREAK-5: one source of truth, no drift between the picker dots and the stats screen).
 *
 * Classification (owner spec + S10 truth table, docs/features/tracking-start-date.md §2,
 * evaluated in order): Feb 29 is always NONE (D1); all of the day's streams read = COMPLETE for
 * any day (pre-start days keep their earned green); a day strictly before the tracking start date
 * is NONE, never MISSED; a past scheduled in-tracking day short of [streamCount] = MISSED; an
 * incomplete today/future day = NONE.
 *
 * D-ALT-6 (Alt Sprint C): the completion threshold is the per-call [streamCount] sourced from the
 * ACTIVE PLAN's descriptor (`streams.size`), NOT a constant or a per-plan subclass — generality
 * flows THROUGH this one predicate, it is never forked (R-STREAK-5). The truth-table ORDER is
 * untouched; only the constant `3` became a parameter. Bible Companion callers pass 3 (parity);
 * M'Cheyne passes 4; a single-stream plan passes 1.
 */
class DayCompletionClassifier
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
    ) {
        fun classify(
            date: LocalDate,
            readCount: Int,
            streamCount: Int,
            today: LocalDate,
            trackingStart: LocalDate?,
        ): DayCompletion =
            when {
                resolver.resolve(date) is ResolvedDate.NoScheduledReadings -> DayCompletion.NONE
                readCount >= streamCount -> DayCompletion.COMPLETE
                // Start-date gate (S10): strictly-before-start days are neutral, never MISSED.
                // Sits AFTER the COMPLETE branch (earned green is kept) and immediately BEFORE
                // the MISSED branch (it only ever suppresses red, never adds it).
                trackingStart != null && date < trackingStart -> DayCompletion.NONE
                date < today -> DayCompletion.MISSED
                else -> DayCompletion.NONE
            }
    }
