package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Live per-day [DayCompletion] for every day of [month] (S8-T2) — the seam the date-picker
 * indicators read, and THE single missed-day predicate (S10): V2 streaks/stats must consume
 * this classification, never re-derive it (R-STREAK-5). One grouped query per month via
 * [ProgressRepository.readCounts]; the flow re-emits when marks OR the tracking start date
 * change, so an open picker stays current. Classification (owner spec + S10 truth table,
 * docs/features/tracking-start-date.md §2, evaluated in order): Feb 29 is always NONE (D1);
 * all three streams read = COMPLETE for any day (pre-start days keep their earned green);
 * a day strictly before the tracking start date is NONE, never MISSED; a past scheduled
 * in-tracking day short of three = MISSED; an incomplete today/future day = NONE.
 */
class GetMonthCompletionUseCase
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(month: YearMonth): Flow<Map<LocalDate, DayCompletion>> {
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            return combine(
                progressRepository.readCounts(start, end),
                settingsRepository.trackingStartDate,
            ) { counts, trackingStart ->
                val today = LocalDate.now(clock)
                buildMap {
                    var date = start
                    while (!date.isAfter(end)) {
                        put(date, classify(date, counts[date] ?: 0, today, trackingStart))
                        date = date.plusDays(1)
                    }
                }
            }
        }

        private fun classify(
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

        private companion object {
            const val STREAM_COUNT = 3
        }
    }
