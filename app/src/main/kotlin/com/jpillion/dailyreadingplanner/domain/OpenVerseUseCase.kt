package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * H6 (BACKLOG #5) — resolves what a VERSE tap in the in-app reader should launch: an external
 * Bible app/site opened at the exact (book, chapter, verse). Data only; the launch side-effect
 * lives in the UI ([com.jpillion.dailyreadingplanner.ui.browser.launchReadingDestination]),
 * reusing the S15 [ReadingDestination] machinery extended to carry a verse.
 *
 * The user's stored [BibleProvider] is read at tap time (parity with [OpenReferenceUseCase]).
 *
 * D-H-4 — IN_APP fallback: a verse-tap-OUT has no external target when the user reads in-app, but
 * the explicit verse tap is a request to leave for an external app. Rather than no-op, fall back
 * to the always-web default ([BibleProvider.BLB]) at that verse. The persisted IN_APP choice is
 * NEVER rewritten — pinned in OpenVerseUseCaseTest.
 */
class OpenVerseUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val urlBuilder: ProviderUrlBuilder,
    ) {
        suspend operator fun invoke(
            reference: Reference,
            verse: Int,
        ): ReadingDestination {
            val stored = settingsRepository.bibleProvider.first()
            // D-H-4: IN_APP has no external verse target — resolve to BLB for the tap-out only.
            val provider = if (stored == BibleProvider.IN_APP) BibleProvider.BLB else stored
            val url = urlBuilder.buildVerse(provider, reference, verse)
            return when (provider) {
                BibleProvider.MYSWORD ->
                    ReadingDestination.MySwordApp(
                        url = url,
                        fallbackUrl = urlBuilder.buildVerse(BibleProvider.BLB, reference, verse),
                    )
                else -> ReadingDestination.Web(url)
            }
        }
    }
