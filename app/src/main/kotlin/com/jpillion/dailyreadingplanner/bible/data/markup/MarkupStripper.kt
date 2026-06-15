package com.jpillion.dailyreadingplanner.bible.data.markup

/**
 * VA-T5 / ESpec-v3 §4.4, D-V3-6 — the reference plain-text derivation. Pure and total: there is
 * no stored `text_plain` column, so plain text for TalkBack (NFR-V3-C) and any future FTS build
 * is `strip(text_markup)`. This is also the gate's strip-invariant reference implementation
 * (`independentPlain == strip(text_markup)` for all 31,102 verses).
 *
 * Strip rules:
 *  - `<a>…</a>` / `<w>…</w>`: drop the tags, KEEP the inner text.
 *  - `<l/>`: collapse to a single space.
 *  - any tag: dropped, inner text kept.
 *  - whitespace normalized (runs collapsed to one space, trimmed).
 */
object MarkupStripper {
    private val LINE_BREAK = Regex("<l\\s*/>")
    private val ANY_TAG = Regex("</?[^>]+>")
    private val WHITESPACE = Regex("\\s+")

    fun strip(markup: String): String {
        var s = LINE_BREAK.replace(markup, " ")
        s = ANY_TAG.replace(s, "")
        return WHITESPACE.replace(s, " ").trim()
    }
}
