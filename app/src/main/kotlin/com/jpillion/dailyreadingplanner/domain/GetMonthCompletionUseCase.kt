package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.platform.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * Live per-day [DayCompletion] for every day of [month] (S8-T2) — the seam the date-picker
 * indicators read. One grouped query per month via [ProgressRepository.readCounts]; the flow
 * re-emits when marks OR the tracking start date change, so an open picker stays current.
 * The classification itself lives in [DayCompletionClassifier] (extracted S11, D-S11-1) so
 * streaks/stats consume the exact same truth table (R-STREAK-5).
 *
 * D-ALT-6/8 (Alt Sprint C): the classifier threshold + the progress scope come from the ACTIVE
 * plan (the descriptor's `streamCount`, the active plan id); a switch re-emits the indicators live.
 * The picker dots inherit N-correctness for free. For the Bible Companion the threshold is 3 (parity).
 */
class GetMonthCompletionUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val activePlanRepository: ActivePlanRepository,
        private val dateProvider: DateProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(month: YearMonth): Flow<Map<LocalDate, DayCompletion>> {
            val start = month.onDay(1)
            val end = month.lastDay
            return activePlanRepository.activePlanId.flatMapLatest { planId ->
                combine(
                    activePlanRepository.activeDescriptor,
                    progressRepository.readCounts(start, end, planId),
                    settingsRepository.trackingStartDate,
                ) { descriptor, counts, trackingStart ->
                    val today = dateProvider.today()
                    val streamCount = descriptor.streamCount
                    buildMap {
                        var date = start
                        while (date <= end) {
                            put(date, classifier.classify(date, counts[date] ?: 0, streamCount, today, trackingStart))
                            date = date.plus(1, DateTimeUnit.DAY)
                        }
                    }
                }
            }
        }
    }
