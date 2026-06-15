package com.jpillion.dailyreadingplanner.bible.data

import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import javax.inject.Inject

/**
 * VB-T2 / ESpec-v3 §4.1 — the only [BibleTextSource] implementation: a thin map from the
 * read-only [VerseDao] range query onto the version-agnostic [VerseText]. Reads [nativeLabel]
 * straight from the row (D-V3-4 — never derives the display number from the verse_id). The
 * Room types ([VerseEntity], [VerseDao]) never escape `bible/data`.
 */
class RoomBibleTextSource
    @Inject
    constructor(
        private val verseDao: VerseDao,
    ) : BibleTextSource {
        override suspend fun getVerses(range: VerseRange): List<VerseText> =
            verseDao
                .getVerses(range.startVerseId, range.endVerseId)
                .map { row ->
                    VerseText(
                        canonicalId = row.verseId,
                        nativeLabel = row.nativeLabel,
                        isTitle = row.isTitle,
                        markup = row.textMarkup,
                    )
                }
    }
