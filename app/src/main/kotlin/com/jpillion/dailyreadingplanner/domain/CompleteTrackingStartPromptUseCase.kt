package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * Persists the first-run prompt's answer (S19, D-S19-1): the chosen tracking start date plus
 * the one-time initialized marker, so the prompt never shows again — including the dismiss
 * path, where the caller passes the Jan-1 fallback (the D-S14-1 default, retained). The
 * date remains fully editable/clearable in Settings afterwards (D-S10-1 marker semantics:
 * a later deliberate clear is never re-defaulted).
 */
class CompleteTrackingStartPromptUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(date: LocalDate) {
            settingsRepository.setTrackingStartDate(date)
            settingsRepository.markTrackingStartInitialized()
        }
    }
