package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import javax.inject.Inject

/**
 * D-N-1 — the text versions bundled in the loaded artifact (in `id` order), via the seam. The
 * reader's version label is sourced from here, never hardcoded; today this is exactly the single
 * KJV row. The reader renders one version as a static title and (D-N-3) more than one as a
 * dropdown — version-switching machinery itself is the deferred V4 work, not built here.
 */
class GetTranslationsUseCase
    @Inject
    constructor(
        private val bibleTextSource: BibleTextSource,
    ) {
        suspend operator fun invoke(): List<BibleTranslation> = bibleTextSource.translations()
    }
