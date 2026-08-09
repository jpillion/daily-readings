package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.di.IoDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D-OT-9 — the real FUMS reporter. A **licence obligation** for showing NKJV/NASB, not telemetry:
 * it tells API.Bible's publishers how much scripture was displayed, and carries no identity of ours
 * (see [FumsIdentity] — two random UUIDs, no PII).
 *
 * The documented manual (non-JavaScript) form, which is the one a native app needs:
 * `GET https://fums.api.bible/f3?t=<fumsToken>&dId=<deviceId>&sId=<sessionId>`
 * — https://docs.api.bible/guides/fair-use/. The `uId` parameter is optional and is deliberately
 * omitted: the app has no accounts, so there is no user to report.
 *
 * Goes DIRECT rather than through the Cloud Run proxy: FUMS needs no API key (the whole reason the
 * proxy exists), so routing it through would add a hop, a cost and a failure point for nothing.
 *
 * **A reporting failure must never break reading.** Every error is swallowed — the reader has
 * already rendered by the time this runs, and a dropped report is a licence-reporting gap, not a
 * reason to deny someone their scripture.
 *
 * p1-03 moved this off `HttpURLConnection` (which does not exist on Kotlin/Native) onto Ktor;
 * see [HttpBibleApiClient] for why, and for the engine choice. The transport changed, the
 * documented URL form did not.
 */
@Singleton
class HttpFumsReporter
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val identity: FumsIdentity,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FumsReporter {
        override suspend fun report(fumsToken: String) {
            if (fumsToken.isBlank()) return
            withContext(ioDispatcher) {
                try {
                    val url =
                        "$FUMS_ENDPOINT?t=${enc(fumsToken)}" +
                            "&dId=${enc(identity.deviceId())}" +
                            "&sId=${enc(identity.sessionId())}"
                    // The response body is of no interest; completing the request is the whole job.
                    httpClient.get(url) {
                        timeout {
                            connectTimeoutMillis = CONNECT_TIMEOUT_MS
                            socketTimeoutMillis = READ_TIMEOUT_MS
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FumsReporter", "usage report dropped")
                }
            }
        }

        /**
         * Ktor's encoder, per ADR-0014 Amendment A1: this file lands in `shared/data`, where Ktor is
         * already a dependency. [com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder]
         * uses the in-house [com.jpillion.dailyreadingplanner.data.reference.PercentEncoder] instead,
         * because it lands in `shared/domain`, where ADR-0001 forbids Ktor.
         *
         * It is NOT byte-identical to the `java.net.URLEncoder` this replaced for three characters:
         * space (`%20` here, `+` there), `~` (passed through here, `%7E` there) and `*` (`%2A` here,
         * passed through there). **None can occur in these three values** — the two ids are
         * generated UUIDs (hex and dashes) and `fumsToken` is an opaque API.Bible token over a
         * base64-family alphabet. `the encoded url form is pinned exactly` in the test measures the
         * real values; `encoding diverges from form encoding only on characters these values cannot
         * contain` measures the divergence itself, so it can never become invisible. If a token ever
         * does carry one of the three, the one-line fix is to call `PercentEncoder.encode` here.
         */
        private fun enc(value: String): String = value.encodeURLParameter()

        private companion object {
            const val FUMS_ENDPOINT = "https://fums.api.bible/f3"
            const val CONNECT_TIMEOUT_MS = 10_000L
            const val READ_TIMEOUT_MS = 10_000L
        }
    }
