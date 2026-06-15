package com.jpillion.dailyreadingplanner.data.reference

import com.jpillion.dailyreadingplanner.bible.domain.ConsecutiveChapterRuns
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Builds the outbound KJV URL for a portion tap, per provider (S13, generalizing Sprint 3's
 * BlbUrlBuilder). Deliberately the ONLY place any provider's URL scheme appears, so a scheme
 * change stays a one-file swap (risk R2, D-S13-2).
 *
 * Portion semantics (spec §6, D-S13-3, pinned in ProviderUrlBuilderTest):
 * - Single-chapter providers (BLB, YouVersion) open the portion's FIRST reference — the
 *   behavior shipped since Sprint 4. The Jun 19 / Dec 19 two-book portion opens 2 John.
 * - Bible Gateway is multi-ref capable: the whole portion rides one passage-search URL —
 *   "Genesis 1-2" for a range, "2 John 1,3 John 1" for the two-book portion (both
 *   live-verified 2026-06-11, docs/data/provider-link-checks.md).
 */
class ProviderUrlBuilder
    @Inject
    constructor() {
        fun build(
            app: ExternalBibleApp,
            portion: Portion,
        ): String =
            when (app) {
                ExternalBibleApp.BLB -> blbUrl(portion.firstRef)
                ExternalBibleApp.YOUVERSION -> youVersionUrl(portion.firstRef)
                ExternalBibleApp.BIBLE_GATEWAY -> bibleGatewayUrl(portion)
                ExternalBibleApp.MYSWORD -> mySwordUrl(portion.firstRef)
            }

        /**
         * H5 (D-H-5) — the VERSE-level deep link for a single (book, chapter, verse), per provider
         * (BACKLOG #5; verse tap in the in-app reader). All four providers support a verse target;
         * the per-provider token columns from [build] are reused (no new catalog — D-S9-1). A
         * superscription tap arrives as verse 0 (the title verse): no provider links a "verse 0",
         * so it is clamped to verse 1 — the chapter's first real verse.
         *
         * Verse forms live-verified 2026-06-15 across providers and awkward books (Psalms, Philemon,
         * 2/3 John) — docs/data/provider-link-checks.md; pinned field-by-field in ProviderUrlBuilderTest.
         * There is no in-app arm here by construction (Sprint K, D-23-1): the in-app destination is a
         * [ReadingDestinationMode], not an [ExternalBibleApp]; OpenVerseUseCase maps in-app mode to
         * BLB (D-H-4) before calling this, so every value here builds a real external URL.
         */
        fun buildVerse(
            app: ExternalBibleApp,
            reference: Reference,
            verse: Int,
        ): String {
            val v = verse.coerceAtLeast(1)
            val book = reference.book
            val ch = reference.chapter
            return when (app) {
                ExternalBibleApp.BLB ->
                    "https://www.blueletterbible.org/kjv/${book.blbAbbrev}/$ch/$v/"
                ExternalBibleApp.YOUVERSION ->
                    "https://www.bible.com/bible/1/${book.usfmCode}.$ch.$v.KJV"
                ExternalBibleApp.MYSWORD ->
                    "https://mysword.info/b?r=${book.order}.$ch.$v"
                ExternalBibleApp.BIBLE_GATEWAY -> {
                    val search = "${book.canonicalName} $ch:$v"
                    val encoded = URLEncoder.encode(search, Charsets.UTF_8.name())
                    "https://www.biblegateway.com/passage/?search=$encoded&version=KJV"
                }
            }
        }

        /** Live-verified Sprint 1 + G-LINKS: 3-letter abbrev, trailing slash. */
        private fun blbUrl(reference: Reference): String =
            "https://www.blueletterbible.org/kjv/${reference.book.blbAbbrev}/${reference.chapter}/"

        /** Live-verified S13: version 1 = KJV, USFM book codes ("PHP", "EZK", "2JN"). */
        private fun youVersionUrl(reference: Reference): String =
            "https://www.bible.com/bible/1/${reference.book.usfmCode}.${reference.chapter}.KJV"

        /**
         * S15, D-S15-1: the vendor-documented NUMERIC reference form — `r={bookNumber}.{chapter}`
         * (mysword-bible.info documents `19.37.3-6` alongside `Psa_37_3-6`, so 19 = Psalms pins
         * the standard 66-book canon order — identical to [Book.order]). Numeric chosen over
         * abbreviations because the vendor publishes NO abbreviation list and MySword links
         * resolve only inside the app (nothing to HTTP-verify): the book number is derived from
         * the already-pinned catalog order (D-S9-1 anti-drift), leaving zero per-book guesses.
         * Pinned for all 66 books in MySwordTokenCatalogTest; the 66-book §3 gate for MySword
         * is the owner's on-device pass (S15 handoff).
         */
        private fun mySwordUrl(reference: Reference): String =
            "https://mysword.info/b?r=${reference.book.order}.${reference.chapter}"

        /**
         * Live-verified S13: full book names in the passage search; consecutive chapters of a
         * book collapse to "Name first-last" (ASCII hyphen — the en dash is display-only,
         * ReadingFormatter's concern); segments join with "," ("2 John 1,3 John 1").
         */
        private fun bibleGatewayUrl(portion: Portion): String {
            val search =
                ConsecutiveChapterRuns.group(portion.refs).joinToString(",") { run ->
                    val book = run.first().book.canonicalName
                    if (run.size == 1) {
                        "$book ${run.first().chapter}"
                    } else {
                        "$book ${run.first().chapter}-${run.last().chapter}"
                    }
                }
            val encoded = URLEncoder.encode(search, Charsets.UTF_8.name())
            return "https://www.biblegateway.com/passage/?search=$encoded&version=KJV"
        }
    }
