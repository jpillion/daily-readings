package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.Portion
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Builds the URL a reading tap should open — the URL only; the Custom-Tab launch side-effect
 * lives in the UI layer (ESpec §8). S13: the destination is the user's chosen [bible
 * provider][com.jpillion.dailyreadingplanner.domain.model.BibleProvider] (BLB by default),
 * read at tap time so a Settings change applies to the very next tap.
 */
class OpenReferenceUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val urlBuilder: ProviderUrlBuilder,
    ) {
        suspend operator fun invoke(portion: Portion): String =
            urlBuilder.build(settingsRepository.bibleProvider.first(), portion)
    }
