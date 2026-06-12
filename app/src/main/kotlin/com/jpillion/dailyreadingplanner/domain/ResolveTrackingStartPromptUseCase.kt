package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * First-run tracking-start prompt gate (S19, D-S19-1 — supersedes the silent auto-write half
 * of D-S14-1; replaces [the S10 initializer's][SettingsRepository.trackingStartInitialized]
 * write-on-launch with an ask-on-launch):
 *
 * - **Already initialized** (the marker is set — any device that ever ran the S10–S18
 *   initializer, or already answered this prompt): never prompt, write nothing.
 * - **Upgrader with existing marks:** never prompt — mark initialized and leave the start
 *   date unset (= track everything = exact pre-S10 behavior; history is not retroactively
 *   neutralized).
 * - **Genuinely fresh install:** prompt. Deliberately persists NOTHING here — the marker is
 *   written only with the user's answer ([CompleteTrackingStartPromptUseCase]), so process
 *   death before an answer simply asks again next launch (an unanswered question is not a
 *   dismissal).
 */
class ResolveTrackingStartPromptUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository,
    ) {
        /** True when the first-run prompt should be shown. */
        suspend operator fun invoke(): Boolean {
            if (settingsRepository.trackingStartInitialized.first()) return false
            if (progressRepository.hasAnyMarks()) {
                settingsRepository.markTrackingStartInitialized()
                return false
            }
            return true
        }
    }
