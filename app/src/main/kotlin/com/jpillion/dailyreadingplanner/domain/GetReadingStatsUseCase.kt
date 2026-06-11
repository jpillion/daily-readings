package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * streak. Marks dated after today never enter the walk (a pre-marked tomorrow is not a
 * streak day yet) but do count toward the year/stream totals.
 */
class GetReadingStatsUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<ReadingStats> {
            val year = LocalDate.now(clock).year
            return combine(
                progressRepository.allReadCounts(),
                progressRepository.streamCounts(
                    start = LocalDate.of(year, 1, 1),
                    end = LocalDate.of(year, 12, 31),
                ),
                settingsRepository.trackingStartDate,
            ) { counts, streamCounts, trackingStart ->
                val today = LocalDate.now(clock)
                val (current, longest) = walkStreaks(counts, today, trackingStart)
                ReadingStats(
                    currentStreakDays = current,
                    longestStreakDays = longest,
                    yearReadCount = streamCounts.values.sum(),
                    streamReadCounts = Stream.entries.associateWith { streamCounts[it] ?: 0 },
                )
            }
        }

        /** Returns (current streak, longest streak) per D-S11-2. */
        private fun walkStreaks(
            counts: Map<LocalDate, Int>,
            today: LocalDate,
            trackingStart: LocalDate?,
        ): Pair<Int, Int> {
            val earliestMark = counts.keys.minOrNull() ?: return 0 to 0
            if (earliestMark.isAfter(today)) return 0 to 0
            var run = 0
            var longest = 0
            var date: LocalDate = earliestMark
            while (!date.isAfter(today)) {
                when (classifier.classify(date, counts[date] ?: 0, today, trackingStart)) {
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
