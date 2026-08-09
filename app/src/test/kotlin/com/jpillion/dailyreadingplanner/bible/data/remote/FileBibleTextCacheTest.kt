package com.jpillion.dailyreadingplanner.bible.data.remote

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.platform.AppFilePaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.CoroutineContext

/**
 * p1-04 — the direct test of [FileBibleTextCache] as an okio component.
 *
 * **Why this file exists at all.** The 14-day rule already had three tests, but they lived in
 * `BibleRemoteModuleTest`, went through the Hilt provider, and backdated entries by 15 and 13 days.
 * That is enough to notice the rule is *present* and nowhere near enough to notice it is *right*:
 * flipping `>` to `>=` — a one-character mutation on a licence obligation — survives every one of
 * them, because no test ever put an entry at exactly `MAX_AGE_MS`. The boundary tests below are
 * the fix, and they are the reason this is a measurement rather than a reassurance.
 *
 * Taking the [FileSystem] as a constructor argument is what makes the two cases that matter
 * expressible at all: an entry of an EXACT age, and an entry whose age is UNKNOWN — the nullable
 * `lastModifiedAtMillis` that `java.io.File.lastModified()` never had.
 */
class FileBibleTextCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val range = VerseId.chapterRange(1, 1) // Genesis 1
    private val verse = VerseText(VerseId.encode(1, 1, 1), "1", false, "In the beginning...")

    private lateinit var cacheRoot: Path

    /** Minimal [AppFilePaths] over a temp dir; only [cache] is exercised here. */
    private inner class TempPaths : AppFilePaths {
        override val databases: Path get() = cacheRoot
        override val cache: Path get() = cacheRoot
        override val files: Path get() = cacheRoot
    }

    /**
     * Rewrites what the cache believes the fetch time to be. [ageMillis] of null means "the
     * platform cannot tell us" — the case okio introduced and `java.io.File` could not express.
     */
    private class AgedFileSystem(
        delegate: FileSystem,
    ) : ForwardingFileSystem(delegate) {
        var override: ((FileMetadata) -> FileMetadata?)? = null

        override fun metadataOrNull(path: Path): FileMetadata? {
            val real = super.metadataOrNull(path) ?: return null
            return override?.invoke(real) ?: real
        }
    }

    private fun setUpCache(fs: FileSystem = FileSystem.SYSTEM): FileBibleTextCache {
        cacheRoot = temp.newFolder("cache").toOkioPath()
        return FileBibleTextCache(fs, TempPaths(), Dispatchers.Unconfined)
    }

    private fun entryFile() =
        FileSystem.SYSTEM
            .list(cacheRoot / "bibletext")
            .single { it.name.contains("nkjv") }

    @Test
    fun `an entry round-trips and lands under the cache directory`() =
        runTest {
            val cache = setUpCache()

            assertThat(cache.get("NKJV", range)).isNull() // cold
            cache.put("NKJV", range, listOf(verse))

            assertThat(cache.get("NKJV", range)).isNotNull().containsExactly(verse)
            assertThat(entryFile().parent).isEqualTo(cacheRoot / "bibletext")
        }

    /**
     * THE boundary, on the strict side. `MAX_AGE_MS` exactly is FRESH, because the shipped rule is
     * `elapsed > MAX_AGE_MS`. Change that `>` to `>=` and this is the only test in the suite that
     * notices — which is precisely why it is here.
     */
    @Test
    fun `an entry exactly MAX_AGE_MS old is still fresh`() =
        runTest {
            val fs = AgedFileSystem(FileSystem.SYSTEM)
            val cache = setUpCache(fs)
            cache.put("NKJV", range, listOf(verse))

            fs.override = { it.copy(lastModifiedAtMillis = System.currentTimeMillis() - MAX_AGE_MS) }

            assertThat(cache.get("NKJV", range)).isNotNull()
            assertThat(FileSystem.SYSTEM.exists(entryFile())).isTrue() // not evicted
        }

    /** THE boundary, on the stale side: one millisecond past the window is a MISS, and is removed. */
    @Test
    fun `an entry one millisecond older than MAX_AGE_MS is a miss and is deleted`() =
        runTest {
            val fs = AgedFileSystem(FileSystem.SYSTEM)
            val cache = setUpCache(fs)
            cache.put("NKJV", range, listOf(verse))
            val entry = entryFile()

            fs.override = { it.copy(lastModifiedAtMillis = System.currentTimeMillis() - MAX_AGE_MS - 1) }

            assertThat(cache.get("NKJV", range)).isNull()
            assertThat(FileSystem.SYSTEM.exists(entry)).isFalse() // cleaned up, not left to rot
        }

    /**
     * The okio-only case, and a deliberate decision rather than an accident of the API: okio
     * reports last-modified as nullable. An entry whose fetch time is UNKNOWN is treated as STALE.
     *
     * The cache is a convenience over a network fetch and the licence obligation runs the other
     * way — serving text of unknown age is the failure that matters, so the safe direction to fail
     * is toward a refetch.
     */
    @Test
    fun `an entry with an unknown last-modified time is treated as stale`() =
        runTest {
            val fs = AgedFileSystem(FileSystem.SYSTEM)
            val cache = setUpCache(fs)
            cache.put("NKJV", range, listOf(verse))
            val entry = entryFile()

            fs.override = { it.copy(lastModifiedAtMillis = null) }

            assertThat(cache.get("NKJV", range)).isNull()
            assertThat(FileSystem.SYSTEM.exists(entry)).isFalse()
        }

    /**
     * The converse, and the reason the read path has no LRU touch: reading an entry must NOT push
     * its refresh deadline out, or the passages someone reads most would never be refreshed.
     */
    @Test
    fun `reading an entry does not extend its refresh deadline`() =
        runTest {
            val cache = setUpCache()
            cache.put("NKJV", range, listOf(verse))
            val entry = entryFile()
            val fetchedAt = FileSystem.SYSTEM.metadata(entry).lastModifiedAtMillis

            assertThat(cache.get("NKJV", range)).isNotNull()

            assertThat(FileSystem.SYSTEM.metadata(entry).lastModifiedAtMillis).isEqualTo(fetchedAt)
        }

    /** Per-version isolation: a cached NKJV chapter must never be served as NASB. */
    @Test
    fun `entries are keyed by version`() =
        runTest {
            val cache = setUpCache()
            cache.put("NKJV", range, listOf(verse))

            assertThat(cache.get("NASB", range)).isNull()
        }

    @Test
    fun `clear removes every entry and is a silent no-op on a cold cache`() =
        runTest {
            val cache = setUpCache()
            cache.clear() // cold: the directory does not exist yet

            cache.put("NKJV", range, listOf(verse))
            cache.clear()

            assertThat(cache.get("NKJV", range)).isNull()
            assertThat(FileSystem.SYSTEM.exists(cacheRoot / "bibletext")).isFalse()
        }

    /** A corrupt entry self-heals into a miss rather than throwing into the reader. */
    @Test
    fun `a corrupt entry reads as a miss and is removed`() =
        runTest {
            val cache = setUpCache()
            cache.put("NKJV", range, listOf(verse))
            val entry = entryFile()
            FileSystem.SYSTEM.write(entry) { writeUtf8("this is not json") }

            assertThat(cache.get("NKJV", range)).isNull()
            assertThat(FileSystem.SYSTEM.exists(entry)).isFalse()
        }

    /**
     * The dispatcher seam, measured rather than assumed. `Dispatchers.IO` does not exist on
     * Kotlin/Native; a body that names it inline compiles fine on Android and fails at a phase
     * boundary months later. Zero dispatches means the seam was bypassed.
     */
    @Test
    fun `every file operation goes through the injected dispatcher`() =
        runTest {
            cacheRoot = temp.newFolder("cache").toOkioPath()
            val probe = CountingDispatcher()
            val cache = FileBibleTextCache(FileSystem.SYSTEM, TempPaths(), probe)

            cache.put("NKJV", range, listOf(verse))
            cache.get("NKJV", range)
            cache.clear()

            assertThat(probe.dispatches >= 3).isTrue()
        }

    /** Counts the blocks handed to the cache's injected dispatcher, then runs them inline. */
    private class CountingDispatcher : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            block.run()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    }
}
