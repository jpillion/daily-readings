package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import com.jpillion.dailyreadingplanner.platform.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * Live [YearStrips] for the current calendar year (S17): one [StripDayState] per stream per
 * calendar day, re-emitting on any mark or tracking-start change (Room invalidation +
 * DataStore, same liveness contract as [GetReadingStatsUseCase]).
 *
 * D-S17-2 — per-stream state THROUGH the shared classifier (R-STREAK-5, no re-derivation):
 * each (day, stream) is classified by feeding [DayCompletionClassifier] a synthetic count —
 * `streamCount` when that one stream is marked, 0 when it isn't — so the truth-table ORDER
 * (Feb 29 → marked/COMPLETE → pre-start gate → past/MISSED → NONE) is inherited verbatim:
 * a marked day is READ even before the tracking start (earned-green parity), Feb 29 is
 * NEUTRAL, an unmarked today is NEUTRAL (grace, R-STREAK-3 spirit), and only a past,
 * post-start, unmarked day is MISSED. The strip can never disagree with the picker dots
 * or the streak walk.
 *
 * D-ALT-8/9 (Alt Sprint C): one strip per ACTIVE-plan stream (in descriptor order), keyed by
 * stream number; the synthetic count uses the descriptor's `streamCount` as the threshold; the
 * progress query is scoped to the active plan. A plan switch re-emits live. For the Bible Companion
 * this is three strips with threshold 3 (parity).
 */
class GetYearStripsUseCase
    @Inject
    constructor(
        private val classifier: DayCompletionClassifier,
        private val progressRepository: ProgressRepository,
        private val settingsRepository: SettingsRepository,
        private val activePlanRepository: ActivePlanRepository,
        private val dateProvider: DateProvider,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(): Flow<YearStrips> {
            val year = dateProvider.today().year
            val jan1 = LocalDate(year, 1, 1)
            val dec31 = LocalDate(year, 12, 31)
            return activePlanRepository.activePlanId.flatMapLatest { planId ->
                combine(
                    activePlanRepository.activeDescriptor,
                    progressRepository.streamMarks(start = jan1, end = dec31, planId = planId),
                    settingsRepository.trackingStartDate,
                ) { descriptor, marks, trackingStart ->
                    val today = dateProvider.today()
                    val dayCount = dec31.dayOfYear
                    val streamCount = descriptor.streamCount
                    YearStrips(
                        year = year,
                        todayIndex = if (today.year == year) today.dayOfYear - 1 else null,
                        dayStates =
                            descriptor.streams.associate { stream ->
                                val marked = marks[stream.number].orEmpty()
                                stream.number to
                                    List(dayCount) { index ->
                                        val date = jan1.plus(index, DateTimeUnit.DAY)
                                        val syntheticCount = if (date in marked) streamCount else 0
                                        when (
                                            classifier.classify(
                                                date,
                                                syntheticCount,
                                                streamCount,
                                                today,
                                                trackingStart,
                                            )
                                        ) {
                                            DayCompletion.COMPLETE -> StripDayState.READ
                                            DayCompletion.MISSED -> StripDayState.MISSED
                                            DayCompletion.NONE -> StripDayState.NEUTRAL
                                        }
                                    }
                            },
                        streams = descriptor.streams,
                    )
                }
            }
        }
    }
