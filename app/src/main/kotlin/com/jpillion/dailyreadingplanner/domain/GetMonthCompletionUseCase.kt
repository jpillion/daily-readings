package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ResolvedDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Live per-day [DayCompletion] for every day of [month] (S8-T2) — the seam the date-picker
 * indicators read. One grouped query per month via [ProgressRepository.readCounts]; the flow
 * re-emits when marks change, so an open picker stays current. Classification (owner spec):
 * all three streams read = COMPLETE for any day; a past scheduled day short of three = MISSED;
 * an incomplete today/future day = NONE; Feb 29 is never anything but NONE (D1).
 */
class GetMonthCompletionUseCase
    @Inject
    constructor(
        private val resolver: ScheduleDateResolver,
        private val progressRepository: ProgressRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(month: YearMonth): Flow<Map<LocalDate, DayCompletion>> {
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            return progressRepository.readCounts(start, end).map { counts ->
                val today = LocalDate.now(clock)
                buildMap {
                    var date = start
                    while (!date.isAfter(end)) {
                        put(date, classify(date, counts[date] ?: 0, today))
                        date = date.plusDays(1)
                    }
                }
            }
        }

        private fun classify(
            date: LocalDate,
            readCount: Int,
            today: LocalDate,
        ): DayCompletion =
            when {
                resolver.resolve(date) is ResolvedDate.NoScheduledReadings -> DayCompletion.NONE
                readCount >= STREAM_COUNT -> DayCompletion.COMPLETE
                date.isBefore(today) -> DayCompletion.MISSED
                else -> DayCompletion.NONE
            }

        private companion object {
            const val STREAM_COUNT = 3
        }
    }
