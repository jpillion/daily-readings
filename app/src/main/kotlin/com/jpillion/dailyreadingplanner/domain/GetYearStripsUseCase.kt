package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Live [YearStrips] for the current calendar year (S17): one [StripDayState] per stream per
 * calendar day, re-emitting on any mark or tracking-start change (Room invalidation +
 * DataStore, same liveness contract as [GetReadingStatsUseCase]).
 *
 * D-S17-2 — per-stream state THROUGH the shared classifier (R-STREAK-5, no re-derivation):
 * each (day, stream) is classified by feeding [DayCompletionClassifier] a synthetic count —
 * STREAM_COUNT when that one stream is marked, 0 when it isn't — so the truth-table ORDER
 * (Feb 29 → marked/COMPLETE → pre-start gate → past/MISSED → NONE) is inherited verbatim:
 * a marked day is READ even before the tracking start (earned-green parity), Feb 29 is
 * NEUTRAL, an unmarked today is NEUTRAL (grace, R-STREAK-3 spirit), and only a past,
 * post-start, unmarked day is MISSED. The strip can never disagree with the picker dots
 * or the streak walk.
 */
class GetYearStripsUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<YearStrips> {
            val year = LocalDate.now(clock).year
            val jan1 = LocalDate.of(year, 1, 1)
            val dec31 = LocalDate.of(year, 12, 31)
            return combine(
                progressRepository.streamMarks(start = jan1, end = dec31),
                settingsRepository.trackingStartDate,
            ) { marks, trackingStart ->
                val today = LocalDate.now(clock)
                val dayCount = jan1.lengthOfYear()
                YearStrips(
                    year = year,
                    todayIndex = if (today.year == year) today.dayOfYear - 1 else null,
                    dayStates =
                        Stream.entries.associateWith { stream ->
                            val marked = marks[stream].orEmpty()
                            List(dayCount) { index ->
                                val date = jan1.plusDays(index.toLong())
                                val syntheticCount =
                                    if (date in marked) DayCompletionClassifier.STREAM_COUNT else 0
                                when (classifier.classify(date, syntheticCount, today, trackingStart)) {
                                    DayCompletion.COMPLETE -> StripDayState.READ
                                    DayCompletion.MISSED -> StripDayState.MISSED
                                    DayCompletion.NONE -> StripDayState.NEUTRAL
                                }
                            }
                        },
                )
            }
        }
    }
