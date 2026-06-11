package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * The engine's front door (ESpec §2): combines the date-anchored schedule with the
 * date-keyed progress store into a live [DayReadings] stream for any calendar date.
 */
class GetDayReadingsUseCase
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
        private val planRepository: ReadingPlanRepository,
        private val progressRepository: ProgressRepository,
    ) {
        operator fun invoke(date: LocalDate): Flow<DayReadings> =
            when (val resolved = resolver.resolve(date)) {
                is ResolvedDate.NoScheduledReadings -> flowOf(DayReadings.NoScheduledReadings(date))
                is ResolvedDate.Scheduled ->
                    progressRepository.streamsRead(date).map { readStreams ->
                        val portions = planRepository.portionsFor(resolved.readingDate)
                        DayReadings.Scheduled(
                            date = date,
                            readings = portions.map { ReadingStatus(it, it.stream in readStreams) },
                            dayComplete = portions.all { it.stream in readStreams },
                        )
                    }
            }
    }
