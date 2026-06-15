package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation

/**
 * D-N-3 — the reader top bar's version state: the bundled versions and the selected one. The
 * stateless [ReaderVersionSelector] renders [available] as a static title when there is one version
 * and as a dropdown when there is more than one. Empty/loading is the brief pre-load state (the
 * selector renders nothing) — it is never null so the screen has no special-casing.
 */
data class ReaderVersionState(
    val available: List<BibleTranslation> = emptyList(),
    val selected: BibleTranslation? = null,
)
