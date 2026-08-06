package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * VB-T2 / ESpec-v3 §4.1 — one verse as the [com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource]
 * seam hands it up. The reader renders [markup] and labels the verse with [nativeLabel]; it MUST NOT
 * assume the displayed number equals `VerseId.verse(canonicalId)`, so a future differently-numbered
 * artifact still displays faithfully (D-V3-4). [isTitle] drives unnumbered-heading rendering (D-V3-7).
 */
data class VerseText(
    val canonicalId: Long,
    val nativeLabel: String,
    val isTitle: Boolean,
    val markup: String,
    /**
     * D-OT-7 / D-OT-10 — an editorial section heading introducing this verse ("Morning Prayer of
     * Trust in God."), rendered as an italic block ABOVE the verse inside this verse's own list
     * item. Null for the bundled KJV, which carries no headings.
     *
     * It is deliberately NOT its own [VerseText]: the reader keys its list by [canonicalId]
     * (D-V3-12) and verse ids are dense, so a mid-chapter heading has no id free between verse 8
     * and verse 9 — a row of its own would be a duplicate key. Carrying it here also means verse
     * selection and clipboard output exclude headings by construction, since both operate on
     * verses.
     *
     * Distinct from [isTitle]: a superscription IS canonical text with a verse id (verse 0); a
     * section heading is editorial matter that is not scripture. Do not conflate them.
     */
    val heading: String? = null,
)
