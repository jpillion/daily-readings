package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import javax.inject.Inject

/**
 * VD-T7 (D-V3-19) — persists the first-run reading-destination answer: the chosen provider plus
 * the one-time completed marker, so the question never shows again. The choice remains editable in
 * Settings afterwards.
 */
class CompleteReadingDestinationPromptUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(provider: BibleProvider) {
            settingsRepository.setBibleProvider(provider)
            settingsRepository.markReadingDestinationPromptCompleted()
        }
    }
