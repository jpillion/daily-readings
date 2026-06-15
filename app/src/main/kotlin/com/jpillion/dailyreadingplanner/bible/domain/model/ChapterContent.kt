package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * VB-T6 — one chapter's worth of rendered-ready verses (incl. any verse-0 superscription as the
 * first [VerseText] with `isTitle = true`), tagged with its book/chapter so the reader can render
 * a header. [bookName] is the catalog canonical name; [verses] are in ascending verse_id order.
 */
data class ChapterContent(
    val bookNo: Int,
    val bookName: String,
    val chapter: Int,
    val verses: List<VerseText>,
)

/**
 * VB-T6 — an ordered list of [ChapterContent] blocks for a whole reading-plan portion (one block
 * per chapter, in portion order; the Jun 19 / Dec 19 two-book portion yields blocks across two
 * books). The reader renders these top-to-bottom (M-V3-4).
 */
data class PortionContent(
    val blocks: List<ChapterContent>,
)
