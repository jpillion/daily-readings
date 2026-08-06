package com.jpillion.dailyreadingplanner.bible.data.remote

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Test

/**
 * D-OT-2 — the owner-specified fallback chain: cache, then network, then bundled KJV with a banner.
 * Each rung is pinned separately, plus the two ways the chain must NOT fire.
 */
class BibleTextResolverTest {
    private val range = VerseId.chapterRange(1, 1) // Genesis 1

    private val kjvVerse = VerseText(VerseId.encode(1, 1, 1), "1", false, "KJV text.")
    private val cachedVerse = VerseText(VerseId.encode(1, 1, 1), "1", false, "Cached NKJV.")

    private class FakeBundled(
        private val verses: List<VerseText>,
    ) : BibleTextSource {
        override suspend fun getVerses(range: VerseRange) = verses

        override suspend fun translations() = listOf(BibleTranslation("KJV", "King James Version"))
    }

    private class FakeApi(
        private val result: PassageResult,
    ) : BibleApiClient {
        var lastRef: String? = null
        var calls = 0

        override suspend fun fetchPassage(
            versionCode: String,
            ref: String,
        ): PassageResult {
            calls++
            lastRef = ref
            return result
        }
    }

    private class FakeCache(
        private var stored: List<VerseText>? = null,
    ) : BibleTextCache {
        var puts = 0

        override suspend fun get(
            versionCode: String,
            range: VerseRange,
        ) = stored

        override suspend fun put(
            versionCode: String,
            range: VerseRange,
            verses: List<VerseText>,
        ) {
            puts++
            stored = verses
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private fun successResult(body: String) =
        PassageResult.Success(
            PassageResponse(
                reference = "Genesis 1",
                content = Json.parseToJsonElement(body).jsonArray,
                copyright = "NKJV copyright",
                fumsToken = "fums-123",
            ),
        )

    private val oneVerseUsx =
        """
        [{"name":"para","type":"tag","attrs":{"style":"p"},"items":[
          {"name":"verse","type":"tag","attrs":{"number":"1","style":"v","sid":"GEN 1:1"},
           "items":[{"text":"1","type":"text"}]},
          {"text":"Fetched NKJV.","type":"text"}]}]
        """.trimIndent()

    private fun resolver(
        api: BibleApiClient = FakeApi(PassageResult.Unavailable),
        cache: BibleTextCache = FakeCache(),
        bundled: BibleTextSource = FakeBundled(listOf(kjvVerse)),
        fums: FumsReporter = NoOpFumsReporter(),
    ) = BibleTextResolver(bundled, api, cache, fums)

    @Test
    fun `bundled version never touches network or cache`() =
        runTest {
            val api = FakeApi(PassageResult.Unavailable)
            val r = resolver(api = api).getVerses(range, BibleVersion.KJV)

            assertThat(api.calls).isEqualTo(0)
            assertThat(r.served).isEqualTo(BibleVersion.KJV)
            assertThat(r.degraded).isFalse()
            assertThat(r.verses).containsExactly(kjvVerse)
        }

    @Test
    fun `a cache hit is served silently and does NOT banner`() =
        runTest {
            val api = FakeApi(PassageResult.Unavailable)
            val r = resolver(api = api, cache = FakeCache(listOf(cachedVerse))).getVerses(range, BibleVersion.NKJV)

            assertThat(api.calls).isEqualTo(0) // cache is consulted before the network
            assertThat(r.verses).containsExactly(cachedVerse)
            assertThat(r.served).isEqualTo(BibleVersion.NKJV)
            // Load-bearing: cached NKJV is real NKJV, so no "displaying KJV" banner.
            assertThat(r.degraded).isFalse()
        }

    @Test
    fun `a network success is served, cached, and reports FUMS`() =
        runTest {
            val cache = FakeCache()
            var reported: String? = null
            val fums =
                object : FumsReporter {
                    override suspend fun report(fumsToken: String) {
                        reported = fumsToken
                    }
                }
            val r =
                resolver(api = FakeApi(successResult(oneVerseUsx)), cache = cache, fums = fums)
                    .getVerses(range, BibleVersion.NKJV)

            assertThat(r.verses.single().markup).isEqualTo("Fetched NKJV.")
            assertThat(r.served).isEqualTo(BibleVersion.NKJV)
            assertThat(r.degraded).isFalse()
            assertThat(r.copyright).isEqualTo("NKJV copyright")
            assertThat(cache.puts).isEqualTo(1)
            assertThat(reported).isEqualTo("fums-123") // licence obligation, D-OT-9
        }

    @Test
    fun `no network and no cache falls back to bundled KJV and banners`() =
        runTest {
            val r =
                resolver(api = FakeApi(PassageResult.Unavailable), cache = FakeCache(null))
                    .getVerses(range, BibleVersion.NKJV)

            // The owner's requirement: show KJV, and say so.
            assertThat(r.verses).containsExactly(kjvVerse)
            assertThat(r.requested).isEqualTo(BibleVersion.NKJV)
            assertThat(r.served).isEqualTo(BibleVersion.KJV)
            assertThat(r.degraded).isTrue()
        }

    @Test
    fun `a genuinely absent passage is empty in the requested version, NOT a fallback`() =
        runTest {
            // NASB Matthew 17:21 does not exist. Nothing failed, so silently swapping in KJV
            // would show the user a verse their chosen translation does not contain.
            val r = resolver(api = FakeApi(PassageResult.NotFound)).getVerses(range, BibleVersion.NASB)

            assertThat(r.verses).isEmpty()
            assertThat(r.served).isEqualTo(BibleVersion.NASB)
            assertThat(r.degraded).isFalse()
        }

    @Test
    fun `an empty successful parse degrades rather than showing a blank page`() =
        runTest {
            val r = resolver(api = FakeApi(successResult("[]"))).getVerses(range, BibleVersion.NKJV)

            assertThat(r.verses).containsExactly(kjvVerse)
            assertThat(r.degraded).isTrue()
        }

    @Test
    fun `the API reference is built from the catalog USFM code`() =
        runTest {
            val api = FakeApi(successResult(oneVerseUsx))
            resolver(api = api).getVerses(VerseId.chapterRange(19, 3), BibleVersion.NKJV)

            // Verse 0 (the superscription slot) must not leak into the request: API.Bible
            // addresses chapters from verse 1.
            assertThat(api.lastRef).isEqualTo("PSA.3.1-PSA.3.999")
        }

    @Test
    fun `a cross-book range becomes one request`() =
        runTest {
            val api = FakeApi(successResult(oneVerseUsx))
            // The Jun 19 / Dec 19 portion: 2 John + 3 John.
            resolver(api = api).getVerses(
                VerseRange(VerseId.encode(63, 1, 1), VerseId.encode(64, 1, 14)),
                BibleVersion.NKJV,
            )

            assertThat(api.lastRef).isEqualTo("2JN.1.1-3JN.1.14")
            assertThat(api.calls).isEqualTo(1)
        }
}
