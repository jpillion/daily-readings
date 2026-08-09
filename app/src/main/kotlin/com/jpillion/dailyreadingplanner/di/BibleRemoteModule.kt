package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.bible.data.remote.BibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.BibleTextResolver
import com.jpillion.dailyreadingplanner.bible.data.remote.DataStoreFumsIdentity
import com.jpillion.dailyreadingplanner.bible.data.remote.FileBibleTextCache
import com.jpillion.dailyreadingplanner.bible.data.remote.FumsIdentity
import com.jpillion.dailyreadingplanner.bible.data.remote.FumsReporter
import com.jpillion.dailyreadingplanner.bible.data.remote.HttpBibleApiClient
import com.jpillion.dailyreadingplanner.bible.data.remote.HttpFumsReporter
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import com.jpillion.dailyreadingplanner.platform.AppFilePaths
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import javax.inject.Singleton

/**
 * Sprint 00R §2 step 1 — Hilt wiring for the online-translations remote stack
 * (spec: `docs/features/online-translations.md`).
 *
 * Separate from [BibleModule] on purpose: that module owns the bundled read-only asset, which is
 * the one thing that must keep working with no network (D-OT-3). This module owns everything that
 * *does* touch the network, so the offline core and the online extra never blur together.
 *
 * Every provider here is `@Provides` rather than `@Binds` because [HttpBibleApiClient] takes
 * non-injectable constructor params (a `String` base URL, a lambda) and deliberately carries no
 * `@Inject`. [FileBibleTextCache]'s params all became injectable in p1-04, but its provider stays
 * `@Provides`: swapping the body to `NoOpBibleTextCache()` is the entire cost of a "caching not
 * permitted" answer from API.Bible, and that stays a one-line edit only while it is a provider.
 *
 * **Nothing injects [BibleTextResolver] yet** — the use-case switch is §2 step 2. This module makes
 * the graph resolvable; it does not change any behaviour on its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BibleRemoteBindsModule {
    /**
     * D-OT-9 — the real FUMS reporter. A licence obligation for showing NKJV/NASB, so it ships WITH
     * the feature, not after it. [NoOpFumsReporter] remains for tests and for any build that must be
     * inert; production reports.
     */
    @Binds
    @Singleton
    abstract fun bindFumsReporter(impl: HttpFumsReporter): FumsReporter

    @Binds
    @Singleton
    abstract fun bindFumsIdentity(impl: DataStoreFumsIdentity): FumsIdentity
}

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
     * p1-03 / ADR-0014 — **the engine boundary**. The HTTP engine is chosen here and nowhere else:
     * [HttpBibleApiClient] and [HttpFumsReporter] take an [HttpClient] and know nothing about which
     * engine backs it, so Phase 2's Darwin engine is a change to this one function.
     *
     * `Android` rather than `OkHttp`, per dependency-contract R3: `ktor-client-android` adds ~26 KB
     * against `ktor-client-okhttp`'s ~927 KB (OkHttp is not on this app's classpath at all today),
     * and it is the `HttpURLConnection`-backed engine — the same transport the app used before
     * p1-03. Release 1.9.0 therefore changes the API without changing what reaches the network.
     *
     * `expectSuccess` is left at Ktor's default of `false` on purpose: both call sites map status
     * codes themselves, and a plugin that threw on 404 would turn `NotFound` into `Unavailable` —
     * a real behaviour change, silently, in the D-OT-2 chain.
     *
     * [HttpTimeout] is installed because both call sites set **per-request** timeouts (the passage
     * fetch reads for 15s, FUMS for 10s, exactly as before); the plugin is what applies them.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        HttpClient(Android) {
            expectSuccess = false
            install(HttpTimeout)
        }

    /**
     * The App Check token supplier is a per-call lambda so §2 step 7 is a change to *this provider*
     * and nothing else — the client, the resolver and the use cases stay untouched.
     *
     * It returns null today, so [HttpBibleApiClient] omits the `X-Firebase-AppCheck` header. That
     * is exactly why the proxy has to stay on `allow` until step 7 lands: a `deny` policy would
     * reject every unattested fetch, and the reader would silently degrade to the KJV banner on
     * every non-KJV read.
     *
     * p1-03 added the [HttpClient] and the `@IoDispatcher` parameters. The dispatcher is injected
     * rather than `Dispatchers.IO`-by-default because `Dispatchers.IO` does not exist on
     * Kotlin/Native, and this compiles fine on Android — which is exactly how it gets missed.
     */
    @Provides
    @Singleton
    fun provideBibleApiClient(
        httpClient: HttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BibleApiClient =
        HttpBibleApiClient(
            httpClient = httpClient,
            baseUrl = PROXY_BASE_URL,
            appCheckTokenProvider = { null },
            ioDispatcher = ioDispatcher,
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
     * [AppFilePaths.cache] rather than [AppFilePaths.files]: this is disposable data and the OS
     * evicting it under storage pressure is correct behaviour for a cache.
     *
     * p1-04 replaced the `Context`-derived directory with the [AppFilePaths] seam and the
     * `@IoDispatcher` qualifier. Same directory on Android — `AppFilePaths.cache` IS
     * `context.cacheDir` — but the location decision now has one home, which matters because on
     * iOS it is the App Group container rather than the sandbox default (D-PORT-4).
     */
    @Provides
    @Singleton
    fun provideBibleTextCache(
        fileSystem: FileSystem,
        appFilePaths: AppFilePaths,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BibleTextCache = FileBibleTextCache(fileSystem, appFilePaths, ioDispatcher)

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
