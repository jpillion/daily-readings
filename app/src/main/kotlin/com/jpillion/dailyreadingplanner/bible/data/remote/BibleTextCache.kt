package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.platform.AppFilePaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D-OT-8 — on-device cache of fetched verses, which is what makes D-OT-2's "no network, cache hit →
 * render silently" case possible.
 *
 * **This is an interface on purpose.** Permission to cache licensed NKJV/NASB text has not been
 * confirmed by API.Bible (the owner is asking). If the answer is no, [NoOpBibleTextCache] is bound
 * instead and the app degrades to the KJV banner on every offline read — a **binding change, not a
 * rewrite**. Nothing above this interface knows which implementation is live.
 */
interface BibleTextCache {
    suspend fun get(
        versionCode: String,
        range: VerseRange,
    ): List<VerseText>?

    suspend fun put(
        versionCode: String,
        range: VerseRange,
        verses: List<VerseText>,
    )

    suspend fun clear()
}

/** The "caching not permitted" binding. Always misses, never stores. */
@Singleton
class NoOpBibleTextCache
    @Inject
    constructor() : BibleTextCache {
        override suspend fun get(
            versionCode: String,
            range: VerseRange,
        ): List<VerseText>? = null

        override suspend fun put(
            versionCode: String,
            range: VerseRange,
            verses: List<VerseText>,
        ) = Unit

        override suspend fun clear() = Unit
    }

@Serializable
private data class CachedVerse(
    val id: Long,
    val label: String,
    val title: Boolean,
    val markup: String,
    val heading: String? = null,
)

/**
 * File-backed cache under the app's cache dir. Deliberately NOT Room: this is disposable data, and
 * a new Room database would mean another schema, another migration surface and another thing that
 * can fail to open (cf. the sprint-00F pre-packaged-schema outage). The OS may evict the cache
 * directory under storage pressure, which for a cache is correct behaviour.
 *
 * Eviction is size-capped LRU by last-modified time, run after writes.
 *
 * p1-04: [FileSystem] + [AppFilePaths] replace `java.io.File`, and the dispatcher arrives through
 * the `@IoDispatcher` qualifier — `Dispatchers.IO` does not exist on Kotlin/Native. The directory
 * layout, file names, LRU rule and [MAX_AGE_MS] are unchanged; the only behavioural decision this
 * conversion had to make is what an UNKNOWN last-modified time means, and it is answered below.
 */
class FileBibleTextCache(
    private val fileSystem: FileSystem,
    private val appFilePaths: AppFilePaths,
    private val ioDispatcher: CoroutineDispatcher,
) : BibleTextCache {
    override suspend fun get(
        versionCode: String,
        range: VerseRange,
    ): List<VerseText>? =
        withContext(ioDispatcher) {
            val f = fileFor(versionCode, range)
            if (!fileSystem.exists(f)) return@withContext null
            // D-OT-8, per API.Bible's published guidance: "refresh cached content every 14 days or
            // less to ensure you receive platform improvements"
            // (https://docs.api.bible/common-questions/). An expired entry is deleted and read as a
            // MISS, so the next online read re-fetches rather than serving stale licensed text
            // indefinitely; with no network it degrades per D-OT-2, which is the honest outcome.
            //
            // NOTE: there is deliberately no LRU touch on read. `lastModified` IS the fetch time,
            // and touching it on every read would push the refresh deadline out forever — the entry
            // would never expire for exactly the passages someone reads most. Eviction below is
            // therefore oldest-fetched-first, which is also the order freshness wants.
            //
            // p1-04: okio reports last-modified as NULLABLE, which `java.io.File.lastModified()`
            // never did. An unknown fetch time is treated as STALE, not fresh: this cache is a
            // convenience over a network fetch, the licence obligation is that stale licensed text
            // is not served, and so the safe direction to fail is toward a refetch.
            val lastModified = fileSystem.metadataOrNull(f)?.lastModifiedAtMillis
            if (lastModified == null || System.currentTimeMillis() - lastModified > MAX_AGE_MS) {
                fileSystem.delete(f, mustExist = false)
                return@withContext null
            }
            runCatching {
                JSON.decodeFromString<List<CachedVerse>>(fileSystem.read(f) { readUtf8() }).map {
                    VerseText(it.id, it.label, it.title, it.markup, it.heading)
                }
            }.getOrElse {
                fileSystem.delete(f, mustExist = false) // corrupt entry self-heals into a miss
                null
            }
        }

    override suspend fun put(
        versionCode: String,
        range: VerseRange,
        verses: List<VerseText>,
    ) {
        withContext(ioDispatcher) {
            runCatching {
                fileSystem.createDirectories(root())
                val payload =
                    verses.map { CachedVerse(it.canonicalId, it.nativeLabel, it.isTitle, it.markup, it.heading) }
                fileSystem.write(fileFor(versionCode, range)) { writeUtf8(JSON.encodeToString(payload)) }
                evictIfOversized()
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) { fileSystem.deleteRecursively(root(), mustExist = false) }
    }

    private fun root() = appFilePaths.cache / DIR

    private fun fileFor(
        versionCode: String,
        range: VerseRange,
    ): Path = root() / "${versionCode.lowercase()}-${range.startVerseId}-${range.endVerseId}.json"

    private fun evictIfOversized() {
        // `File.listFiles()` returned null for a missing directory; `listOrNull` is the same
        // contract. `File.lastModified()` and `File.length()` both returned 0 when unknown, which
        // is what the `?: 0L` fallbacks preserve — an unknown-age entry sorts oldest and is
        // evicted first, matching the old ordering exactly.
        val files =
            fileSystem
                .listOrNull(root())
                ?.map { it to fileSystem.metadataOrNull(it) }
                ?.sortedBy { (_, meta) -> meta?.lastModifiedAtMillis ?: 0L }
                ?: return
        var total = files.sumOf { (_, meta) -> meta?.size ?: 0L }
        for ((path, meta) in files) {
            if (total <= MAX_BYTES) break
            total -= meta?.size ?: 0L
            fileSystem.delete(path, mustExist = false)
        }
    }

    private companion object {
        const val DIR = "bibletext"
        const val MAX_BYTES = 12L * 1024 * 1024

        /**
         * API.Bible asks for a refresh "every 14 days or less". Their other stated limit — "fewer
         * than 500 consecutive verses" — is satisfied by construction: one entry is one
         * `VerseRange`, i.e. a single chapter or verse window, and the longest chapter in scripture
         * is Psalm 119 at 176 verses.
         */
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
