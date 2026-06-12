package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves what a reading tap should launch — data only; the launch side-effect lives in the
 * UI layer (ESpec §8). S13: the destination is the user's chosen
 * [BibleProvider] (BLB by default), read at tap time so a Settings change applies to the
 * very next tap. S15 (D-S15-3): an installed-app provider (MySword) resolves to
 * [ReadingDestination.MySwordApp], carrying the BLB URL as the uninstalled-fallback — the
 * persisted choice itself is never rewritten here.
 */
class OpenReferenceUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val urlBuilder: ProviderUrlBuilder,
    ) {
        suspend operator fun invoke(portion: Portion): ReadingDestination {
            val provider = settingsRepository.bibleProvider.first()
            val url = urlBuilder.build(provider, portion)
            return when (provider) {
                BibleProvider.MYSWORD ->
                    ReadingDestination.MySwordApp(
                        url = url,
                        fallbackUrl = urlBuilder.build(BibleProvider.BLB, portion),
                    )
                else -> ReadingDestination.Web(url)
            }
        }
    }
