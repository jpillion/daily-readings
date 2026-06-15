package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.Book
import javax.inject.Inject

/**
 * VB-T6 — (book, chapter) → [ChapterContent] via the seam. The whole-chapter range starts at
 * verse 0, so a superscription (if any) arrives as the first verse with `isTitle = true`. Returns
 * an empty verse list only if the chapter is genuinely absent (the asset guarantees coverage).
 */
class GetChapterUseCase
    @Inject
    constructor(
        private val bibleTextSource: BibleTextSource,
    ) {
        suspend operator fun invoke(
            book: Book,
            chapter: Int,
        ): ChapterContent {
            require(chapter in 1..book.chapterCount) {
                "${book.canonicalName} ch $chapter out of range"
            }
            val verses = bibleTextSource.getVerses(VerseId.chapterRange(book.order, chapter))
            return ChapterContent(
                bookNo = book.order,
                bookName = book.canonicalName,
                chapter = chapter,
                verses = verses,
            )
        }
    }
