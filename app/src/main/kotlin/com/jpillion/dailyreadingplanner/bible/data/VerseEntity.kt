package com.jpillion.dailyreadingplanner.bible.data

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * VA-T6 / ESpec-v3 §4.2 — maps one row of the read-only `verse` table in the bundled bible.db
 * asset. verse=0 ⇒ superscription; [isTitle]=true drives unnumbered-heading rendering (D-V3-7),
 * never the magic verse==0. [textMarkup] is the closed-tag display form (D-V3-6, no text_plain).
 *
 * The composite primary key (translation_id, verse_id) matches the asset DDL exactly.
 */
@Entity(tableName = "verse", primaryKeys = ["translation_id", "verse_id"])
data class VerseEntity(
    @ColumnInfo(name = "translation_id") val translationId: Int,
    @ColumnInfo(name = "verse_id") val verseId: Long,
    @ColumnInfo(name = "book_no") val bookNo: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    @ColumnInfo(name = "native_label") val nativeLabel: String,
    @ColumnInfo(name = "is_title") val isTitle: Boolean,
    @ColumnInfo(name = "text_markup") val textMarkup: String,
)
