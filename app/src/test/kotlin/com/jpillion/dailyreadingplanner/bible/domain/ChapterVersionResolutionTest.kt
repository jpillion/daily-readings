package com.jpillion.dailyreadingplanner.bible.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpBibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpFumsReporter
import com.jpillion.dailyreadingplanner.bible.data.remote.PassageResult
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Sprint 00R step 2/3 — the use cases now go through [BibleTextResolver] for the user's *selected*
 * version, and [com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent] carries the
 * version actually served so the D-OT-2 banner is derived per block.
 *
 * Deliberately not re-testing the fallback chain itself (that is `BibleTextResolverTest`); these
 * pin the wiring between the setting, the resolver and the returned content.
 */
class ChapterVersionResolutionTest {
    private val genesis = BookCatalog.requireByName("Genesis")
    private val settings = FakeSettingsRepository()

    private val nkjvVerse = VerseText(1_001_001L, "1", false, "Remote NKJV text.")

    /** Always reports the passage as unfetchable, so the resolver must fall back to bundled KJV. */
    private object OfflineApi : BibleApiClient {
        override suspend fun fetchPassage(
            versionCode: String,
            ref: String,
        ): PassageResult = PassageResult.Unavailable
    }

    /** Pre-populated cache, so a non-KJV read succeeds without a network. */
    private class WarmCache(
        private val verses: List<VerseText>,
    ) : BibleTextCache {
        override suspend fun get(
            versionCode: String,
            range: VerseRange,
        ) = verses

        override suspend fun put(
            versionCode: String,
            range: VerseRange,
            verses: List<VerseText>,
        ) = Unit

        override suspend fun clear() = Unit
    }

    private fun useCase(
        api: BibleApiClient = OfflineApi,
        cache: BibleTextCache = NoOpBibleTextCache(),
    ) = GetChapterUseCase(
        BibleTextResolver(FakeBibleTextSource(), api, cache, NoOpFumsReporter()),
        settings,
    )

    /**
     * The default: nothing stored, so KJV is served and the banner stays off. This is the
     * "an upgrader sees no change" pin — it must survive every later step of the sprint.
     */
    @Test
    fun `default selection serves bundled KJV and is not degraded`() =
        runTest {
            val content = useCase()(genesis, 1)

            assertThat(content.requestedVersion).isEqualTo(BibleVersion.KJV)
            assertThat(content.servedVersion).isEqualTo(BibleVersion.KJV)
            assertThat(content.degraded).isFalse()
            assertThat(content.verses).isNotEmpty()
        }

    /**
     * D-OT-2 case 3 — NKJV selected, no network, no cache: the reader still gets text (bundled
     * KJV), and `degraded` is true so the banner fires. A silent translation swap would be a real
     * defect, which is exactly what this pins against.
     */
    @Test
    fun `selected NKJV with no network falls back to KJV and reports degraded`() =
        runTest {
            settings.storedBibleVersion.value = BibleVersion.NKJV

            val content = useCase()(genesis, 1)

            assertThat(content.requestedVersion).isEqualTo(BibleVersion.NKJV)
            assertThat(content.servedVersion).isEqualTo(BibleVersion.KJV)
            assertThat(content.degraded).isTrue()
            assertThat(content.verses).isNotEmpty() // never a blank page
        }

    /** D-OT-2 case 2 — cache hit is real NKJV text, so it is served silently with NO banner. */
    @Test
    fun `selected NKJV served from cache is not degraded`() =
        runTest {
            settings.storedBibleVersion.value = BibleVersion.NKJV

            val content = useCase(cache = WarmCache(listOf(nkjvVerse)))(genesis, 1)

            assertThat(content.servedVersion).isEqualTo(BibleVersion.NKJV)
            assertThat(content.degraded).isFalse()
            assertThat(content.verses).containsExactly(nkjvVerse)
        }

    /** The selection is read at load time, so changing it takes effect on the next chapter load. */
    @Test
    fun `selection is read per load, not captured once`() =
        runTest {
            val uc = useCase()

            assertThat(uc(genesis, 1).requestedVersion).isEqualTo(BibleVersion.KJV)

            settings.storedBibleVersion.value = BibleVersion.NASB

            assertThat(uc(genesis, 1).requestedVersion).isEqualTo(BibleVersion.NASB)
        }
}
