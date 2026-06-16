package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Live [ReadingStats] derived purely from stored marks (R-STREAK-6) — re-emits whenever
 * marks or the tracking start date change (FR-17), so stats can never contradict the
 * date-picker indicators: both consume the same [DayCompletionClassifier] (R-STREAK-5).
 *
 * Streak walk (D-S11-2): one forward pass over every calendar date from the earliest stored
 * mark to today, classifying each day. COMPLETE extends the run; MISSED resets it; NONE is
 * neutral — skipped without extending or breaking. The truth table makes the product rules
 * fall out: Feb 29 is NONE (R-STREAK-2), an incomplete today is NONE = grace (R-STREAK-3),
 * walking real dates crosses Dec 31 -> Jan 1 natively (R-STREAK-4), and pre-start incomplete
 * days are NONE while pre-start complete days still extend (R-STREAK-5, earned-green parity
 * with the picker). The current streak is simply the run still open at today. Flooring the
 * walk at the earliest mark is exact: no earlier day can be COMPLETE, so none can extend a
 * streak. Marks dated after today never enter the walk but do count toward the year/stream totals.
 *
 * D-ALT-7/8 (Alt Sprint C): the denominators come from the ACTIVE plan's descriptor
 * (`dayCount × streamCount`, `dayCount`); the classifier threshold is `streamCount`; the
 * per-stream rows iterate the descriptor's streams; every progress query is scoped to the active
 * plan. A plan switch flips the active id/descriptor and the whole flow re-emits live. For the
 * Bible Companion these resolve to 1,095 / 365 / 3 streams exactly (parity).
 */
class GetReadingStatsUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val activePlanRepository: ActivePlanRepository,
        private val clock: Clock,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(): Flow<ReadingStats> {
            val year = LocalDate.now(clock).year
            return activePlanRepository.activePlanId.flatMapLatest { planId ->
                combine(
                    activePlanRepository.activeDescriptor,
                    progressRepository.allReadCounts(planId),
                    progressRepository.streamCounts(
                        start = LocalDate.of(year, 1, 1),
                        end = LocalDate.of(year, 12, 31),
                        planId = planId,
                    ),
                    settingsRepository.trackingStartDate,
                ) { descriptor, counts, streamCounts, trackingStart ->
                    val today = LocalDate.now(clock)
                    val (current, longest) = walkStreaks(counts, descriptor.streamCount, today, trackingStart)
                    ReadingStats(
                        currentStreakDays = current,
                        longestStreakDays = longest,
                        yearReadCount = streamCounts.values.sum(),
                        streamReadCounts =
                            descriptor.streams.associate { it.number to (streamCounts[it.number] ?: 0) },
                        streams = descriptor.streams,
                        yearTotalReadings = descriptor.dayCount * descriptor.streamCount,
                        streamTotalDays = descriptor.dayCount,
                    )
                }
            }
        }

        /** Returns (current streak, longest streak) per D-S11-2; the classifier threshold is [streamCount]. */
        private fun walkStreaks(
            counts: Map<LocalDate, Int>,
            streamCount: Int,
            today: LocalDate,
            trackingStart: LocalDate?,
        ): Pair<Int, Int> {
            val earliestMark = counts.keys.minOrNull() ?: return 0 to 0
            if (earliestMark.isAfter(today)) return 0 to 0
            var run = 0
            var longest = 0
            var date: LocalDate = earliestMark
            while (!date.isAfter(today)) {
                when (classifier.classify(date, counts[date] ?: 0, streamCount, today, trackingStart)) {
                    DayCompletion.COMPLETE -> {
                        run++
                        if (run > longest) longest = run
                    }
                    DayCompletion.MISSED -> run = 0
                    // Neutral (Feb 29, pre-start, in-grace today): neither extends nor breaks.
                    DayCompletion.NONE -> Unit
                }
                date = date.plusDays(1)
            }
            return run to longest
        }
    }
