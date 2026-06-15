package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * VD-T10 (OQ-2) — one-time "the in-app Bible is here" note for EXISTING users. Shown once to an
 * upgrader (a user with existing marks) who never saw the first-run reading-destination question.
 * Their external provider choice is preserved and never switched silently; the note is purely
 * informational (with an optional "use it now" action handled in the UI).
 *
 * DELIBERATELY mutually exclusive with the fresh-install first-run question
 * ([ResolveReadingDestinationPromptUseCase]) via the [ProgressRepository.hasAnyMarks] split — a
 * fresh install (no marks) gets the question, an upgrader (marks) gets this note, never both.
 */
class ResolveUpgradeNoteUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository,
    ) {
        /** True when the one-time upgrade note should be shown. */
        suspend operator fun invoke(): Boolean {
            if (settingsRepository.upgradeNoteShown.first()) return false
            if (settingsRepository.readingDestinationPromptCompleted.first()) return false
            return progressRepository.hasAnyMarks()
        }
    }
