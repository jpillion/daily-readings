package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * VB-T6 — one chapter's worth of rendered-ready verses (incl. any verse-0 superscription as the
 * first [VerseText] with `isTitle = true`), tagged with its book/chapter so the reader can render
 * a header. [bookName] is the catalog canonical name; [verses] are in ascending verse_id order.
 *
 * D-OT-5 — the block also records which version was *asked for* and which was *actually served*,
 * so the D-OT-2 banner is derived per block rather than tracked as shared mutable state. With a
 * pager rendering several pages, a single "did the last load fail" flag would race and banner the
 * wrong page. Both default to the bundled KJV, so every pre-existing construction site (and every
 * KJV-only test) is untouched.
 */
data class ChapterContent(
    val bookNo: Int,
    val bookName: String,
    val chapter: Int,
    val verses: List<VerseText>,
    val requestedVersion: BibleVersion = BibleVersion.DEFAULT,
    val servedVersion: BibleVersion = BibleVersion.DEFAULT,
    /** Licence obligation (D-OT-9): must be displayed wherever non-KJV text is shown. */
    val copyright: String? = null,
) {
    /** True when D-OT-2's KJV fallback fired for this block, i.e. the banner must be shown. */
    val degraded: Boolean get() = servedVersion != requestedVersion
}

/**
 * VB-T6 — an ordered list of [ChapterContent] blocks for a whole reading-plan portion (one block
 * per chapter, in portion order; the Jun 19 / Dec 19 two-book portion yields blocks across two
 * books). The reader renders these top-to-bottom (M-V3-4).
 */
data class PortionContent(
    val blocks: List<ChapterContent>,
)
