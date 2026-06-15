package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * A bundled text version, sourced from the asset's `translation` table (D-N-1) — never a
 * hardcoded literal. Today the asset carries exactly one row: KJV / "King James Version".
 *
 * [code] is the compact display label shown inline in the reader top bar ("KJV", D-N-2);
 * [name] is the unabbreviated form spoken to TalkBack ("King James Version"). When a future
 * artifact bundles more than one version, [com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderVersionSelector]
 * switches from a static title to a dropdown over these (D-N-3) — version-switching machinery
 * itself is the deferred V4 multi-translation work and is NOT built here.
 */
data class BibleTranslation(
    val code: String,
    val name: String,
)
