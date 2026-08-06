package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.data.remote.BibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpBibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpFumsReporter
import com.jpillion.dailyreadingplanner.bible.data.remote.PassageResult

/**
 * Sprint 00R step 2 — wraps a bundled [BibleTextSource] in the resolver the use cases now inject,
 * so KJV-only tests read exactly as they did before the resolver existed.
 *
 * The API client **throws on contact** rather than returning `Unavailable`. A test that only ever
 * asks for KJV must never reach the network (D-OT-3, and KJV's `requiresNetwork = false` short-
 * circuits the chain before the first rung), so a call here means the bundled short-circuit broke —
 * which should fail loudly rather than pass quietly through the KJV fallback, where a genuine
 * regression would be indistinguishable from correct behaviour.
 */
fun bundledResolver(source: BibleTextSource): BibleTextResolver =
    BibleTextResolver(
        bundled = source,
        api =
            object : BibleApiClient {
                override suspend fun fetchPassage(
                    versionCode: String,
                    ref: String,
                ): PassageResult = throw AssertionError("a KJV-only test must not reach the network (D-OT-3)")
            },
        cache = NoOpBibleTextCache(),
        fums = NoOpFumsReporter(),
    )
