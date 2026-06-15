package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
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
 * Sprint K (D-23-1) — a verse tap is ALWAYS external by definition: the reader IS the in-app
 * destination, so tapping a verse to "open it elsewhere" can only mean an external app. The
 * resolution therefore depends ONLY on the chosen [ExternalBibleApp] (read at tap time, parity
 * with [OpenReferenceUseCase]) and is independent of the [ReadingDestinationMode] — the verse
 * destination never resolves to the in-app reader. This supersedes the H4 "map IN_APP -> BLB"
 * shim: in-app-mode users keep their remembered external app (the default BLB for a legacy
 * in-app user, whose external app degraded to BLB), and the persisted choice is never rewritten.
 *
 * MySword resolves to [ReadingDestination.MySwordApp] with a BLB-verse browser fallback; every
 * other external app to [ReadingDestination.Web].
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
            // D-23-1: verse taps are always external — resolve from the external-app axis alone,
            // never the mode. The in-app reader is never a verse-tap target.
            val app = settingsRepository.externalBibleApp.first()
            val url = urlBuilder.buildVerse(app, reference, verse)
            return when (app) {
                ExternalBibleApp.MYSWORD ->
                    ReadingDestination.MySwordApp(
                        url = url,
                        fallbackUrl = urlBuilder.buildVerse(ExternalBibleApp.BLB, reference, verse),
                    )
                else -> ReadingDestination.Web(url)
            }
        }
    }
