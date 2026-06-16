package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Marks or unmarks ALL of the active plan's readings for the exact calendar date in one
 * transaction (FR-3) — the widget's whole-day seam (the on-screen button was removed in Sprint H).
 *
 * D-ALT-11 (Alt Sprint C): "all readings" is the active plan's `1..N` streams (read from the active
 * descriptor), not the retired enum's fixed three — so a 4-stream M'Cheyne day marks four, a
 * single-stream plan marks one. The work is scoped to the active plan's partition (D-ALT-8/12).
 */
class MarkWholeDayUseCase
    @Inject
    constructor(
        private val progressRepository: ProgressRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            markRead: Boolean,
        ) {
            val descriptor = activePlanRepository.activeDescriptor.first()
            val planId = activePlanRepository.activePlanId.first()
            progressRepository.setWholeDay(
                date = date,
                streamNumbers = descriptor.streams.map { it.number },
                isRead = markRead,
                planId = planId,
            )
        }
    }
