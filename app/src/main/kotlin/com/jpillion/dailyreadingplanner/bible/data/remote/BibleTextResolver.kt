package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

/**
 * What the reader actually got, and in which version.
 *
 * [degraded] is derived rather than tracked as separate mutable state: with a pager rendering
 * several pages, a shared "did the last load fail" flag would race and banner the wrong page.
 */
data class ResolvedVerses(
    val verses: List<VerseText>,
    val requested: BibleVersion,
    val served: BibleVersion,
    val copyright: String? = null,
) {
    /** True when D-OT-2's KJV fallback fired, i.e. the banner must be shown. */
    val degraded: Boolean get() = served != requested
}

/**
 * D-OT-5 — the layer above [BibleTextSource]: *"verses for the user's **selected** version, with
 * cache and fallback"*, as opposed to the seam's *"verses from **this** artifact"*.
 *
 * The D-OT-2 chain, in order, exactly as the owner specified:
 *  1. bundled version selected → bundled source, done.
 *  2. cache hit → serve it, silently. No banner: this is real NKJV/NASB text.
 *  3. network success → serve, populate cache.
 *  4. anything else → serve **bundled KJV** with [degraded] set, which raises the banner.
 *
 * Step 4 is why KJV must stay bundled (D-OT-3): the fallback needs a target that cannot itself fail.
 */
class BibleTextResolver(
    private val bundled: BibleTextSource,
    private val api: BibleApiClient,
    private val cache: BibleTextCache,
    private val fums: FumsReporter,
) {
    suspend fun getVerses(
        range: VerseRange,
        requested: BibleVersion,
    ): ResolvedVerses {
        if (!requested.requiresNetwork) {
            return ResolvedVerses(bundled.getVerses(range), requested, requested)
        }

        cache.get(requested.code, range)?.let { cached ->
            return ResolvedVerses(cached, requested, requested)
        }

        val ref = apiRef(range) ?: return fallback(range, requested)
        return when (val result = api.fetchPassage(requested.code, ref)) {
            is PassageResult.Success -> {
                val parsed =
                    UsxTransformer.transform(result.passage.content) {
                        BookCatalog.findByUsfm(it)?.order
                    }
                // A successful fetch that yields nothing usable is a degrade, not an empty chapter:
                // showing a blank page would look like the app lost the scripture.
                if (parsed.verses.isEmpty()) return fallback(range, requested)

                // Licence obligation (D-OT-9) — reported for text actually shown to the user.
                result.passage.fumsToken?.let { fums.report(it) }
                cache.put(requested.code, range, parsed.verses)
                ResolvedVerses(parsed.verses, requested, requested, result.passage.copyright)
            }
            // Genuinely absent in this translation (NASB Matthew 17:21). Honest empty result in
            // the requested version — NOT a fallback, because nothing failed.
            PassageResult.NotFound -> ResolvedVerses(emptyList(), requested, requested)
            PassageResult.Unavailable -> fallback(range, requested)
        }
    }

    private suspend fun fallback(
        range: VerseRange,
        requested: BibleVersion,
    ) = ResolvedVerses(
        verses = bundled.getVerses(range),
        requested = requested,
        served = BibleVersion.KJV,
    )

    /**
     * `VerseRange` → an API.Bible passage id ("GEN.1.1-GEN.2.25"). Book codes come from the ONE
     * catalog via `Book.usfmCode` (D-S13-1). Returns null if either end will not resolve, so a
     * malformed range degrades rather than fetching the wrong passage.
     */
    private fun apiRef(range: VerseRange): String? {
        val start = point(range.startVerseId) ?: return null
        val end = point(range.endVerseId) ?: return null
        return if (start == end) start else "$start-$end"
    }

    private fun point(id: Long): String? {
        val usfm = BookCatalog.books.firstOrNull { it.order == VerseId.book(id) }?.usfmCode ?: return null
        // Verse 0 is the app's superscription slot; API.Bible addresses chapters from verse 1.
        val verse = VerseId.verse(id).coerceAtLeast(1)
        return "$usfm.${VerseId.chapter(id)}.$verse"
    }
}

/**
 * D-OT-9 — API.Bible's FUMS usage reporting. A licence obligation, not telemetry: it reports that
 * licensed text was displayed, and carries no user identity of ours.
 *
 * An interface so the resolver stays testable offline and so a reporting failure can never break
 * reading — see [NoOpFumsReporter].
 */
interface FumsReporter {
    suspend fun report(fumsToken: String)
}

/** Used in tests and wherever reporting must be inert. */
class NoOpFumsReporter : FumsReporter {
    override suspend fun report(fumsToken: String) = Unit
}
