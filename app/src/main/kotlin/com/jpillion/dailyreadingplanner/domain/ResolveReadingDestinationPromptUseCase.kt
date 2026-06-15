package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * VD-T7 (D-V3-19) — first-run reading-destination gate. A genuinely fresh install is asked once,
 * "read in the in-app Bible or open an external app?"; the answer writes `bible_provider`
 * ([CompleteReadingDestinationPromptUseCase]). In-app is NEVER a silent default — nothing is
 * persisted here, so process death before an answer simply asks again next launch.
 *
 * Fresh-install vs. upgrader is the [ProgressRepository.hasAnyMarks] split (mirrors S19): a user
 * with any existing marks is an upgrader (they get the one-time upgrade note instead — a
 * DELIBERATELY mutually-exclusive gate, [ResolveUpgradeNoteUseCase]) and is never re-prompted nor
 * switched. The completed marker guards against re-asking after an answer.
 */
class ResolveReadingDestinationPromptUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository,
    ) {
        /** True when the first-run reading-destination question should be shown. */
        suspend operator fun invoke(): Boolean {
            if (settingsRepository.readingDestinationPromptCompleted.first()) return false
            if (progressRepository.hasAnyMarks()) return false
            return true
        }
    }
