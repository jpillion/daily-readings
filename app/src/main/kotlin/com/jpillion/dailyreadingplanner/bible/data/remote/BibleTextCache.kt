package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
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
 * can fail to open (cf. the sprint-00F pre-packaged-schema outage). The OS may evict `cacheDir`
 * under storage pressure, which for a cache is correct behaviour.
 *
 * Eviction is size-capped LRU by last-modified time, run after writes.
 */
class FileBibleTextCache(
    private val cacheDir: File,
) : BibleTextCache {
    override suspend fun get(
        versionCode: String,
        range: VerseRange,
    ): List<VerseText>? =
        withContext(Dispatchers.IO) {
            val f = fileFor(versionCode, range)
            if (!f.exists()) return@withContext null
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
            if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) {
                f.delete()
                return@withContext null
            }
            runCatching {
                JSON.decodeFromString<List<CachedVerse>>(f.readText()).map {
                    VerseText(it.id, it.label, it.title, it.markup, it.heading)
                }
            }.getOrElse {
                f.delete() // corrupt entry self-heals into a miss
                null
            }
        }

    override suspend fun put(
        versionCode: String,
        range: VerseRange,
        verses: List<VerseText>,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                root().mkdirs()
                val payload =
                    verses.map { CachedVerse(it.canonicalId, it.nativeLabel, it.isTitle, it.markup, it.heading) }
                fileFor(versionCode, range).writeText(JSON.encodeToString(payload))
                evictIfOversized()
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { root().deleteRecursively() }
    }

    private fun root() = File(cacheDir, DIR)

    private fun fileFor(
        versionCode: String,
        range: VerseRange,
    ) = File(root(), "${versionCode.lowercase()}-${range.startVerseId}-${range.endVerseId}.json")

    private fun evictIfOversized() {
        val files = root().listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
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
