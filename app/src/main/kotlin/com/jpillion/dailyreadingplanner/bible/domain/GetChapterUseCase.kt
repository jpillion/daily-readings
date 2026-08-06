package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.Book
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * VB-T6 — (book, chapter) → [ChapterContent] via the seam. The whole-chapter range starts at
 * verse 0, so a superscription (if any) arrives as the first verse with `isTitle = true`. Returns
 * an empty verse list only if the chapter is genuinely absent (the asset guarantees coverage).
 */
class GetChapterUseCase
    @Inject
    constructor(
        private val resolver: BibleTextResolver,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(
            book: Book,
            chapter: Int,
        ): ChapterContent {
            require(chapter in 1..book.chapterCount) {
                "${book.canonicalName} ch $chapter out of range"
            }
            // Sprint 00R step 2 — read the selection at load time rather than holding a cached
            // copy, the D-S13-4 idiom: a version change in the selector takes effect on the next
            // chapter load with no invalidation protocol between the two.
            val requested = settingsRepository.selectedBibleVersion.first()
            val resolved = resolver.getVerses(VerseId.chapterRange(book.order, chapter), requested)
            return ChapterContent(
                bookNo = book.order,
                bookName = book.canonicalName,
                chapter = chapter,
                verses = resolved.verses,
                requestedVersion = resolved.requested,
                servedVersion = resolved.served,
                copyright = resolved.copyright,
            )
        }
    }
