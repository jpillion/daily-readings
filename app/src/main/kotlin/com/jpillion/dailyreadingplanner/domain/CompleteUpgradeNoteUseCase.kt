package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import javax.inject.Inject

/**
 * VD-T10 (OQ-2; Sprint K D-23-1) — records that the one-time upgrade note has been shown (so it
 * never re-shows) and, if the user chose to switch to the in-app reader from the note, sets the
 * destination mode to IN_APP. Dismissing the note marks it shown WITHOUT touching either
 * destination axis — the existing external choice (mode + app) is preserved.
 */
class CompleteUpgradeNoteUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /** [switchToInApp] true only when the user tapped "use it now"; dismiss passes false. */
        suspend operator fun invoke(switchToInApp: Boolean) {
            if (switchToInApp) {
                settingsRepository.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            }
            settingsRepository.markUpgradeNoteShown()
        }
    }
