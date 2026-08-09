package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.platform.DateProvider
import javax.inject.Inject

/**
 * "Reset progress" (S8, owner decision): clears every mark in the *current* year only —
 * the date provider decides the year at invocation time; prior years' history is preserved.
 */
class ResetYearProgressUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
        private val dateProvider: DateProvider,
    ) {
        suspend operator fun invoke() {
            progressRepository.clearYear(dateProvider.today().year)
        }
    }
