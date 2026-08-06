package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
 */
@Singleton
class HttpFumsReporter
    @Inject
    constructor(
        private val identity: FumsIdentity,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FumsReporter {
        override suspend fun report(fumsToken: String) {
            if (fumsToken.isBlank()) return
            withContext(ioDispatcher) {
                var conn: HttpURLConnection? = null
                try {
                    val url =
                        URL(
                            "$FUMS_ENDPOINT?t=${enc(fumsToken)}" +
                                "&dId=${enc(identity.deviceId())}" +
                                "&sId=${enc(identity.sessionId())}",
                        )
                    conn =
                        (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                        }
                    // The response body is of no interest; reading the status is what completes the
                    // request, and draining the stream lets the connection be reused.
                    conn.responseCode
                    conn.inputStream?.use { it.readBytes() }
                } catch (e: Exception) {
                    android.util.Log.w("FumsReporter", "usage report dropped")
                } finally {
                    conn?.disconnect()
                }
            }
        }

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        private companion object {
            const val FUMS_ENDPOINT = "https://fums.api.bible/f3"
            const val CONNECT_TIMEOUT_MS = 10_000
            const val READ_TIMEOUT_MS = 10_000
        }
    }
