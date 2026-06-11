package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import java.time.LocalDate
import javax.inject.Inject

/** Marks or unmarks all three readings for the exact calendar date in one transaction (FR-3). */
class MarkWholeDayUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            markRead: Boolean,
        ) {
            progressRepository.setWholeDay(date, markRead)
        }
    }
