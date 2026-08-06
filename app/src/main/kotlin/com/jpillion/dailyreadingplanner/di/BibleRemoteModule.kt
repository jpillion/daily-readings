package com.jpillion.dailyreadingplanner.di

import android.content.Context
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.data.remote.FileBibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.FumsReporter
import com.jpillion.dailyreadingplanner.bible.data.remote.HttpBibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.NoOpFumsReporter
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Sprint 00R §2 step 1 — Hilt wiring for the online-translations remote stack
 * (spec: `docs/features/online-translations.md`).
 *
 * Separate from [BibleModule] on purpose: that module owns the bundled read-only asset, which is
 * the one thing that must keep working with no network (D-OT-3). This module owns everything that
 * *does* touch the network, so the offline core and the online extra never blur together.
 *
 * Every provider here is `@Provides` rather than `@Binds` because [HttpBibleApiClient] and
 * [FileBibleTextCache] take non-injectable constructor params (a `String` base URL, a `File`, a
 * lambda) and deliberately carry no `@Inject`.
 *
 * **Nothing injects [BibleTextResolver] yet** — the use-case switch is §2 step 2. This module makes
 * the graph resolvable; it does not change any behaviour on its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object BibleRemoteModule {
    /**
     * The Cloud Run proxy that holds the API.Bible key (`backend/README.md`). The key never ships
     * in the app, which is the whole reason the proxy exists.
     *
     * ⚠️ The proxy currently runs with `POLICY_ON_ATTESTATION_FAIL=allow`, i.e. this endpoint is
     * publicly reachable, because App Check is not configured yet and the client below has no token
     * to send. Configuring App Check and returning the policy to `deny` is §2 step 7.
     */
    const val PROXY_BASE_URL = "https://drp-bible-proxy-954215684233.us-central1.run.app"

    /**
     * The App Check token supplier is a per-call lambda so §2 step 7 is a change to *this provider*
     * and nothing else — the client, the resolver and the use cases stay untouched.
     *
     * It returns null today, so [HttpBibleApiClient] omits the `X-Firebase-AppCheck` header. That
     * is exactly why the proxy has to stay on `allow` until step 7 lands: a `deny` policy would
     * reject every unattested fetch, and the reader would silently degrade to the KJV banner on
     * every non-KJV read.
     */
    @Provides
    @Singleton
    fun provideBibleApiClient(): BibleApiClient =
        HttpBibleApiClient(
            baseUrl = PROXY_BASE_URL,
            appCheckTokenProvider = { null },
        )

    /**
     * D-OT-8 — the caching binding, and the only place the licensing answer lands.
     *
     * API.Bible has not yet confirmed that on-device caching of NKJV/NASB text is permitted (the
     * owner is asking; implementation was directed to proceed ahead of the reply). If the answer is
     * **no**, swap the body of this one function to `NoOpBibleTextCache()`: D-OT-2 case 2 then
     * disappears and every offline read falls to the KJV banner. That is the entire cost of a "no" —
     * a binding change, never a rewrite. Nothing above [BibleTextCache] knows which impl is live.
     *
     * `cacheDir` rather than `filesDir`: this is disposable data and the OS evicting it under
     * storage pressure is correct behaviour for a cache.
     */
    @Provides
    @Singleton
    fun provideBibleTextCache(
        @ApplicationContext context: Context,
    ): BibleTextCache = FileBibleTextCache(context.cacheDir)

    /**
     * D-OT-9 — **not done yet.** FUMS reporting is a licence obligation, not telemetry, and it ships
     * with the feature (§2 step 6), not after it. Until a real reporter exists this binding is inert,
     * so the resolver's `fums.report(...)` call is a no-op rather than a missing call site: step 6
     * replaces this provider and needs to touch nothing else.
     */
    @Provides
    @Singleton
    fun provideFumsReporter(): FumsReporter = NoOpFumsReporter()

    /**
     * D-OT-5 — the layer the use cases will inject in §2 step 2, in place of [BibleTextSource].
     *
     * [bundled] is the existing `RoomBibleTextSource` binding: the resolver's fallback target, and
     * the reason KJV must stay bundled (D-OT-3) — the last rung of the D-OT-2 chain has to be
     * something that cannot itself fail.
     */
    @Provides
    @Singleton
    fun provideBibleTextResolver(
        bundled: BibleTextSource,
        api: BibleApiClient,
        cache: BibleTextCache,
        fums: FumsReporter,
    ): BibleTextResolver =
        BibleTextResolver(
            bundled = bundled,
            api = api,
            cache = cache,
            fums = fums,
        )
}
