package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Marks or unmarks a single reading for the exact calendar date (FR-2), in the ACTIVE plan's
 * partition (D-ALT-8/12). A stream is its plain `Int` number now (D-ALT-5).
 */
class ToggleReadingUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            streamNumber: Int,
            markRead: Boolean,
        ) {
            val planId = activePlanRepository.activePlanId.first()
            progressRepository.setRead(date, streamNumber, markRead, planId)
        }
    }
