package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.model.Stream
import java.time.LocalDate
import javax.inject.Inject

/** Marks or unmarks a single reading for the exact calendar date (FR-2). */
class ToggleReadingUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            stream: Stream,
            markRead: Boolean,
        ) {
            progressRepository.setRead(date, stream, markRead)
        }
    }
