package com.jpillion.dailyreadingplanner.bible.data.markup

/**
 * VA-T5 / ESpec-v3 §4.4 — the CLOSED, versioned markup tag vocabulary, defined in exactly one
 * place. Three parties consume it: the import pipeline (`tools/build_bible_db.py`, emits only
 * these tags), the renderer (Sprint C `VerseRenderer`), and the gate's strip invariant
 * (`BibleTextVerificationTest`). Adding a tag is a versioned edit here, never a silent artifact
 * change (FR-V3-6).
 *
 * V3.0 P0 ships ONLY [ADDED]. [WORDS_OF_CHRIST] and [LINE_BREAK] are reserved (recognized by
 * the renderer from day one) but the importer emits them only if a corpus carries them cleanly
 * (OQ-4, P1, droppable). The chosen primary corpus (open-bibles KJV OSIS) carries `<l>` poetry
 * and `<q who="Jesus">` red-letter, but V3.0 deliberately emits neither (P0 scope).
 */
object BibleMarkup {
    /** Translator-added word (KJV italics). `<a>…</a>`. P0 — shipped in V3.0. */
    const val ADDED = "a"

    /** Words of Christ (red-letter). `<w>…</w>`. P1 — reserved, off by default. */
    const val WORDS_OF_CHRIST = "w"

    /** Poetic line break. `<l/>` (self-closing). P1 — reserved. */
    const val LINE_BREAK = "l"

    /** The complete closed vocabulary. Any tag outside this set fails the gate (FR-V3-6). */
    val CLOSED_TAGS: Set<String> = setOf(ADDED, WORDS_OF_CHRIST, LINE_BREAK)
}
