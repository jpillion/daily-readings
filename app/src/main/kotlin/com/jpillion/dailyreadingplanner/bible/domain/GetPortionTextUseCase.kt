package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.PortionContent
import com.jpillion.dailyreadingplanner.domain.model.Portion
import javax.inject.Inject

/**
 * VB-T6 / FR-V3-2, M-V3-4 — a whole reading-plan [Portion] → ordered [PortionContent] for the
 * reader to render top-to-bottom. One block per chapter-reference in portion order (multi-chapter
 * portions AND the two-book Jun 19 / Dec 19 portion). Block order mirrors the plan's ref order.
 */
class GetPortionTextUseCase
    @Inject
    constructor(
        private val bridge: PortionVerseBridge,
        private val bibleTextSource: BibleTextSource,
    ) {
        suspend operator fun invoke(portion: Portion): PortionContent {
            val ranges = bridge.rangesFor(portion)
            val blocks =
                portion.refs.mapIndexed { index, ref ->
                    val verses = bibleTextSource.getVerses(ranges[index])
                    ChapterContent(
                        bookNo = ref.book.order,
                        bookName = ref.book.canonicalName,
                        chapter = ref.chapter,
                        verses = verses,
                    )
                }
            return PortionContent(blocks)
        }
    }
