package com.jpillion.dailyreadingplanner.di

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpFumsReporter
import com.jpillion.dailyreadingplanner.bible.data.remote.PassageResult
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 00R §2 step 1 — pins the remote-stack WIRING, as opposed to the components themselves
 * (already pinned by `BibleTextResolverTest` and `UsxTransformerTest`).
 *
 * The repo has no Hilt test runner, so the module's `@Provides` functions are called directly —
 * which is what Hilt does anyway, and keeps the test on the JVM/Robolectric pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BibleRemoteModuleTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val range = VerseId.chapterRange(1, 1) // Genesis 1
    private val kjvVerse = VerseText(VerseId.encode(1, 1, 1), "1", false, "Bundled KJV text.")

    private class FakeBundled(
        private val verses: List<VerseText>,
    ) : BibleTextSource {
        override suspend fun getVerses(range: VerseRange) = verses

        override suspend fun translations() = listOf(BibleTranslation("KJV", "King James Version"))
    }

    /** Fails the test if the resolver reaches the network for a bundled version. */
    private object ForbiddenApi : BibleApiClient {
        override suspend fun fetchPassage(
            versionCode: String,
            ref: String,
        ): PassageResult = throw AssertionError("KJV must never hit the network (D-OT-3)")
    }

    /**
     * The base URL is concatenated as `"$baseUrl/v1/passage?..."` in `HttpBibleApiClient`, so a
     * trailing slash silently produces `//v1/passage`. Pins the shape rather than restating the
     * literal, which would only re-assert the constant against itself.
     */
    @Test
    fun `proxy base URL is https and safe to concatenate`() {
        assertThat(BibleRemoteModule.PROXY_BASE_URL).startsWith("https://")
        assertThat(BibleRemoteModule.PROXY_BASE_URL).doesNotContain(" ")
        assertThat(BibleRemoteModule.PROXY_BASE_URL.endsWith("/")).isFalse()
    }

    /**
     * D-OT-8 — the provided cache is a real, working, `cacheDir`-rooted store.
     *
     * **This test is expected to go red if the licensing answer is "no"** and the provider is
     * swapped to `NoOpBibleTextCache`. That is the point: turning caching off is a deliberate
     * recorded decision, not something that can happen by accident.
     */
    @Test
    fun `provided cache round-trips and lives under the app cache dir`() =
        runTest {
            val cache = BibleRemoteModule.provideBibleTextCache(context)

            assertThat(cache.get("NKJV", range)).isNull() // cold
            cache.put("NKJV", range, listOf(kjvVerse))

            assertThat(cache.get("NKJV", range)).containsExactly(kjvVerse)
            assertThat(context.cacheDir.walkTopDown().any { it.isFile && it.name.contains("nkjv") }).isTrue()

            cache.clear()
            assertThat(cache.get("NKJV", range)).isNull()
        }

    /** Per-version isolation: a cached NKJV chapter must never be served as NASB. */
    @Test
    fun `provided cache keys by version`() =
        runTest {
            val cache = BibleRemoteModule.provideBibleTextCache(context)
            cache.put("NKJV", range, listOf(kjvVerse))

            assertThat(cache.get("NASB", range)).isNull()
        }

    /**
     * The resolver provider hands the *bundled* source into the fallback slot: a KJV request is
     * served from it without touching [ForbiddenApi]. Catches a resolver wired to the wrong source
     * — the failure mode that would break D-OT-2's last rung, where the fallback must not fail.
     */
    @Test
    fun `provided resolver serves a bundled version from the bundled source, offline`() =
        runTest {
            val resolver =
                BibleRemoteModule.provideBibleTextResolver(
                    bundled = FakeBundled(listOf(kjvVerse)),
                    api = ForbiddenApi,
                    cache = BibleRemoteModule.provideBibleTextCache(context),
                    fums = NoOpFumsReporter(),
                )

            val resolved = resolver.getVerses(range, BibleVersion.KJV)

            assertThat(resolved.verses).containsExactly(kjvVerse)
            assertThat(resolved.served).isEqualTo(BibleVersion.KJV)
            assertThat(resolved.degraded).isFalse()
        }
}
