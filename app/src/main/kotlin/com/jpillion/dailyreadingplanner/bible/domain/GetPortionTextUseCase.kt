package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.PortionContent
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.Portion
import kotlinx.coroutines.flow.first
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
        private val resolver: BibleTextResolver,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(portion: Portion): PortionContent {
            val ranges = bridge.rangesFor(portion)
            // Read once for the whole portion, not per block: a version change landing mid-render
            // would otherwise be able to serve two blocks of one reading in different versions.
            val requested = settingsRepository.selectedBibleVersion.first()
            val blocks =
                portion.refs.mapIndexed { index, ref ->
                    val resolved = resolver.getVerses(ranges[index], requested)
                    ChapterContent(
                        bookNo = ref.book.order,
                        bookName = ref.book.canonicalName,
                        chapter = ref.chapter,
                        verses = resolved.verses,
                        requestedVersion = resolved.requested,
                        servedVersion = resolved.served,
                        copyright = resolved.copyright,
                    )
                }
            return PortionContent(blocks)
        }
    }
