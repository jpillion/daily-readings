package com.jpillion.dailyreadingplanner.domain.model

/**
 * What a reading tap should launch (S15, D-S15-3). The UI layer owns the actual launch
 * side-effect (ESpec §8); this model only carries the data it needs.
 *
 * MySword is the first installed-app provider (spec §11 go-path): its URL means nothing on
 * the web (mysword.info serves an install-instructions stub, verified 2026-06-11), so the
 * tap must fire an explicit-component intent into the app — and if that app has been
 * uninstalled since the user chose it, the tap falls back to opening [MySwordApp.fallbackUrl]
 * (the Blue Letter Bible URL) in the browser, NEVER the stub page. The persisted provider
 * choice is left untouched (reinstalling MySword restores it; meanwhile every tap behaves
 * like BLB) — pinned in OpenReferenceUseCaseTest.
 */
sealed interface ReadingDestination {
    /** A plain https URL for the Custom-Tab path — every web provider (BLB, BG, YouVersion). */
    data class Web(
        val url: String,
    ) : ReadingDestination

    /** An explicit intent into the MySword app, with a BLB browser fallback if it's gone. */
    data class MySwordApp(
        val url: String,
        val fallbackUrl: String,
    ) : ReadingDestination

    /** Render the whole portion in the in-app reader (V3, D-V3-18) — carries the portion, not a URL. */
    data class InApp(
        val portion: Portion,
    ) : ReadingDestination

    companion object {
        /** MySword's package — also the manifest `<queries>` entry (package visibility, API 30+). */
        const val MYSWORD_PACKAGE = "com.riversoft.android.mysword"

        /** The vendor-documented link activity (mysword-bible.info "Link or open MySword"). */
        const val MYSWORD_LINK_ACTIVITY = "com.riversoft.android.mysword.MySwordLink"
    }
}
