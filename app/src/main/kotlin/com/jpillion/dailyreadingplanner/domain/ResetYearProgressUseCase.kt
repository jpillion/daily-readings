package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * "Reset progress" (S8, owner decision): clears every mark in the *current* year only —
 * the clock decides the year at invocation time; prior years' history is preserved.
 */
class ResetYearProgressUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke() {
            progressRepository.clearYear(LocalDate.now(clock).year)
        }
    }
