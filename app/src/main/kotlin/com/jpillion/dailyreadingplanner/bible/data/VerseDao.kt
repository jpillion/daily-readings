package com.jpillion.dailyreadingplanner.bible.data

import androidx.room.Dao
import androidx.room.Query

/**
 * VA-T6 / ESpec-v3 §4.2 — read-only access to the bundled KJV asset. The range query is the
 * primitive the seam ([com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource]) sits on;
 * everything is addressed by canonical verse_id so a chapter range [encode(b,c,0)..encode(b,c,999)]
 * naturally includes the verse-0 superscription. Rows come back in ascending verse_id order.
 */
@Dao
interface VerseDao {
    @Query(
        "SELECT * FROM verse WHERE translation_id = 1 AND verse_id BETWEEN :startId AND :endId " +
            "ORDER BY verse_id ASC",
    )
    suspend fun getVerses(
        startId: Long,
        endId: Long,
    ): List<VerseEntity>

    @Query(
        "SELECT * FROM verse WHERE translation_id = 1 AND book_no = :bookNo AND chapter = :chapter " +
            "ORDER BY verse_id ASC",
    )
    suspend fun getChapter(
        bookNo: Int,
        chapter: Int,
    ): List<VerseEntity>
}
