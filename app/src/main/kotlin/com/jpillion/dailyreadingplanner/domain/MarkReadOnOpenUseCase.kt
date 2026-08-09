package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * Marks a reading read as a side-effect of opening it (D-O-1): one-way (never unmarks),
 * idempotent, in the ACTIVE plan's partition (D-ALT-8/12). A stream is its plain `Int` number
 * now (D-ALT-5).
 */
class MarkReadOnOpenUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            streamNumber: Int,
        ) {
            val planId = activePlanRepository.activePlanId.first()
            // Always read = true (D-O-1): the dedicated mark-on-open use case never unmarks.
            progressRepository.setRead(date, streamNumber, isRead = true, planId)
        }
    }
