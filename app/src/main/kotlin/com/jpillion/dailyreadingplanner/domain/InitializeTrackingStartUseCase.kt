package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * One-time tracking-start default (S10 D-S10-1, default value superseded by S14 D-S14-1):
 * on a genuinely fresh install — never initialized AND zero existing marks — default the
 * tracking start date to **January 1 of the current year** (owner decision, S14; was the
 * install date in S10–S13). The user can still change or clear it in Settings. An install
 * with existing progress (an upgrader) is left unset (= track everything = exact pre-S10
 * behavior). The separate "initialized" marker makes this idempotent and guarantees a user
 * who deliberately clears the date is never re-defaulted.
 *
 * Already-initialized devices (D-S14-1): the store records only the date + the boolean
 * marker — an auto-set install date is indistinguishable from a user's deliberate choice
 * of that same date, so existing devices keep their current value; only fresh installs get
 * the Jan-1 default. No migration.
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
                settingsRepository.setTrackingStartDate(LocalDate.of(LocalDate.now(clock).year, 1, 1))
            }
        }
    }
