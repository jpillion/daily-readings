package com.jpillion.dailyreadingplanner.domain

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
 * indicators read. One grouped query per month via [ProgressRepository.readCounts]; the flow
 * re-emits when marks OR the tracking start date change, so an open picker stays current.
 * The classification itself lives in [DayCompletionClassifier] (extracted S11, D-S11-1) so
 * streaks/stats consume the exact same truth table (R-STREAK-5).
 */
class GetMonthCompletionUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
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
                        put(date, classifier.classify(date, counts[date] ?: 0, today, trackingStart))
                        date = date.plusDays(1)
                    }
                }
            }
        }
    }
