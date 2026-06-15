package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves what a reading tap should launch — data only; the launch side-effect lives in the
 * UI layer (ESpec §8). The destination is read at tap time so a Settings change applies to the
 * very next tap.
 *
 * Sprint K (D-23-1) — the resolution now branches on the [ReadingDestinationMode] axis first:
 * - [ReadingDestinationMode.IN_APP] → [ReadingDestination.InApp] (the whole portion, no URL).
 * - [ReadingDestinationMode.EXTERNAL] → a URL for the chosen [ExternalBibleApp]; MySword
 *   resolves to [ReadingDestination.MySwordApp] carrying the BLB URL as the uninstalled-fallback
 *   (D-S15-3), every other external app to [ReadingDestination.Web]. The persisted choice itself
 *   is never rewritten here.
 *
 * This mode-first branch is the only place the mode decides web-vs-reader for a portion tap.
 */
class OpenReferenceUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val urlBuilder: ProviderUrlBuilder,
    ) {
        suspend operator fun invoke(portion: Portion): ReadingDestination {
            // D-23-1: branch on the mode BEFORE touching the external app — the in-app reader
            // carries the whole portion, not a URL (the URL builder has no in-app scheme).
            if (settingsRepository.readingDestinationMode.first() == ReadingDestinationMode.IN_APP) {
                return ReadingDestination.InApp(portion)
            }
            val app = settingsRepository.externalBibleApp.first()
            val url = urlBuilder.build(app, portion)
            return when (app) {
                ExternalBibleApp.MYSWORD ->
                    ReadingDestination.MySwordApp(
                        url = url,
                        fallbackUrl = urlBuilder.build(ExternalBibleApp.BLB, portion),
                    )
                else -> ReadingDestination.Web(url)
            }
        }
    }
