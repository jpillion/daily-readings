package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

/**
 * The engine's front door (ESpec §2): combines the date-anchored schedule with the
 * date-keyed progress store into a live [DayReadings] stream for any calendar date.
 *
 * D-ALT-8/17/22 (Alt Sprint C): everything is scoped to the ACTIVE plan. The active id +
 * descriptor come from [ActivePlanRepository] (default `bible_companion` → byte-for-byte parity);
 * a plan switch flips [ActivePlanRepository.activePlanId] and the whole flow re-emits the new
 * plan's day live (D-D-3 liveness). Each reading carries its stream TITLE, resolved here from the
 * active descriptor (D-C-6) — null for a single-stream plan (D-ALT-23), so the UI never looks a
 * title up. Progress is read for the active plan id.
 */
class GetDayReadingsUseCase
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
        private val planRepository: ReadingPlanRepository,
        private val progressRepository: ProgressRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(date: LocalDate): Flow<DayReadings> =
            when (val resolved = resolver.resolve(date)) {
                is ResolvedDate.NoScheduledReadings -> flowOf(DayReadings.NoScheduledReadings(date))
                is ResolvedDate.Scheduled ->
                    activePlanRepository.activePlanId.flatMapLatest { planId ->
                        combine(
                            activePlanRepository.activeDescriptor,
                            progressRepository.streamsRead(date, planId),
                        ) { descriptor, readStreams ->
                            val portions = planRepository.portionsFor(planId, resolved.readingDate)
                            DayReadings.Scheduled(
                                date = date,
                                readings =
                                    portions.map { portion ->
                                        ReadingStatus(
                                            portion = portion,
                                            isRead = portion.streamNumber in readStreams,
                                            streamTitle = descriptor.titleFor(portion.streamNumber),
                                        )
                                    },
                                dayComplete = portions.all { it.streamNumber in readStreams },
                            )
                        }
                    }
            }
    }

/**
 * The descriptor's title for [streamNumber], or null when the plan has a single (unlabeled) stream
 * (D-ALT-23) — a lone reading needs no "which stream" label. A blank declared title is also null.
 */
internal fun PlanDescriptor.titleFor(streamNumber: Int): String? {
    if (streamCount <= 1) return null
    val title = streams.firstOrNull { it.number == streamNumber }?.title
    return title?.takeIf { it.isNotBlank() }
}
