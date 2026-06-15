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
)
