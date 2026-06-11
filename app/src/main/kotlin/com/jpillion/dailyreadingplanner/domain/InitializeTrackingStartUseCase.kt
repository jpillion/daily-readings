package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * One-time tracking-start default (S10, D-S10-1 = spec §3 option B): on a genuinely fresh
 * install — never initialized AND zero existing marks — default the tracking start date to
 * today, so a mid-year adopter never sees months of red they never intended to track.
 * An install with existing progress (an upgrader) is left unset (= track everything =
 * exact pre-S10 behavior). The separate "initialized" marker makes this idempotent and
 * guarantees a user who deliberately clears the date is never re-defaulted.
 */
class InitializeTrackingStartUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke() {
            if (settingsRepository.trackingStartInitialized.first()) return
            settingsRepository.markTrackingStartInitialized()
            if (!progressRepository.hasAnyMarks()) {
                settingsRepository.setTrackingStartDate(LocalDate.now(clock))
            }
        }
    }
