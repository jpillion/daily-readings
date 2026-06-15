package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import javax.inject.Inject

/**
 * VD-T7 (D-V3-19; Sprint K D-23-1) — persists the first-run reading-destination answer: the chosen
 * [ReadingDestinationMode] plus the one-time completed marker, so the question never shows again.
 * The choice remains editable in Settings afterwards.
 *
 * The fresh-install question is binary (in-app vs. an external app/site); only the mode is set
 * here. When the answer is EXTERNAL, the external app keeps its default (BLB) — the user refines
 * *which* external app later in Settings. In-app is NEVER a silent default: nothing is persisted
 * unless the user answers.
 */
class CompleteReadingDestinationPromptUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(mode: ReadingDestinationMode) {
            settingsRepository.setReadingDestinationMode(mode)
            settingsRepository.markReadingDestinationPromptCompleted()
        }
    }
