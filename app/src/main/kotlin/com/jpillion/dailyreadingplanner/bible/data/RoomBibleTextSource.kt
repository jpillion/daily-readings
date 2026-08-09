package com.jpillion.dailyreadingplanner.bible.data

import androidx.sqlite.db.SimpleSQLiteQuery
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * VB-T2 / ESpec-v3 §4.1 — the only [BibleTextSource] implementation: a thin map from the
 * read-only [VerseDao] range query onto the version-agnostic [VerseText]. Reads [nativeLabel]
 * straight from the row (D-V3-4 — never derives the display number from the verse_id). The
 * Room types ([VerseEntity], [VerseDao]) never escape `bible/data`.
 *
 * D-N-1: [translations] reads the asset's `translation` table directly via the database's
 * support-SQLite handle (a raw read, NOT a Room `@Entity`), so the version label is sourced from
 * the data without adding a mapped table — which would alter Room's schema validation / the pinned
 * `room_master_table` identity hash. The read runs off the main thread.
 */
class RoomBibleTextSource
    @Inject
    constructor(
        private val verseDao: VerseDao,
        private val database: BibleDatabase,
        // p1-04: `Dispatchers.IO` does not exist on Kotlin/Native. The qualifier is the one place
        // the platform answer lives; on Android it still resolves to Dispatchers.IO.
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

        override suspend fun translations(): List<BibleTranslation> =
            withContext(ioDispatcher) {
                val cursor =
                    database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT code, name FROM translation ORDER BY id ASC"),
                    )
                cursor.use {
                    val out = mutableListOf<BibleTranslation>()
                    val codeIdx = it.getColumnIndexOrThrow("code")
                    val nameIdx = it.getColumnIndexOrThrow("name")
                    while (it.moveToNext()) {
                        out.add(BibleTranslation(code = it.getString(codeIdx), name = it.getString(nameIdx)))
                    }
                    out
                }
            }
    }
