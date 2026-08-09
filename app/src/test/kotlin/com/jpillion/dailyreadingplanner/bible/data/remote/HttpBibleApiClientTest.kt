package com.jpillion.dailyreadingplanner.bible.data.remote

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * p1-03 — the passage fetch, which **had no tests at all** before Ktor.
 *
 * That absence is the reason this file matters more than a like-for-like port would suggest. The
 * status-code mapping is not plumbing: `200` -> parse, `404` -> `NotFound`, anything else ->
 * `Unavailable` is the **product behaviour** that drives D-OT-2's offline fallback and the
 * "Unable to download NKJV, displaying KJV" banner. Until now, breaking it broke nothing in the
 * suite — a user would have seen KJV with a banner forever and no build would have gone red.
 *
 * **R10 — what would be wrong if the bytes stopped going through Ktor.** Every assertion below runs
 * through `MockEngine`, which is an `HttpClientEngine`: it only ever observes requests that Ktor's
 * pipeline dispatches to it. If someone reinstated `HttpURLConnection` — or built the URL by any
 * route other than the injected [HttpClient] — `engine.requestHistory` would be **empty** and these
 * tests would fail loudly rather than passing blind. That is deliberately not true of the fakes
 * used elsewhere in the suite, which sit *above* [BibleApiClient] and cannot see the transport.
 *
 * **Robolectric, and only because of `android.util.Log`.** The degrade branch logs, and a plain JVM
 * unit test throws `Method w in android.util.Log not mocked` — which the client's own
 * catch-everything would then swallow, so `every other status degrades to Unavailable` would pass
 * for the wrong reason whether the mapping worked or not. Under this runner the log call succeeds
 * and the assertion measures the mapping. Unnecessary once ADR-0014's `Logger` seam lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HttpBibleApiClientTest {
    private val baseUrl = "https://proxy.example"

    /**
     * An extension on [TestScope] so the client's IO dispatcher shares `runTest`'s scheduler —
     * otherwise `withContext` in the client trips "Detected use of different schedulers".
     */
    private fun TestScope.client(
        engine: MockEngine,
        token: String? = null,
        tokenProvider: (suspend () -> String?)? = null,
    ) = HttpBibleApiClient(
        httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(HttpTimeout)
            },
        baseUrl = baseUrl,
        appCheckTokenProvider = tokenProvider ?: { token },
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun engineReturning(
        status: HttpStatusCode,
        body: String = "",
    ) = MockEngine { respond(body, status) }

    private val successBody =
        """
        {
          "reference": "Genesis 1",
          "content": [{"name":"para","items":[]}],
          "copyright": "NKJV (c) Thomas Nelson",
          "fumsToken": "fums-1"
        }
        """.trimIndent()

    // --- The request itself. These are the assertions that cannot pass without Ktor. ---

    /**
     * URL shape **and query-parameter order**, exactly as shipped. Concatenated rather than built
     * through Ktor's parameter API precisely so this string is unchanged; if a future edit routes it
     * through `parameters.append` the order or the encoding could move without anyone noticing.
     */
    @Test
    fun `the request url and query parameter order are pinned exactly`() =
        runTest {
            val engine = engineReturning(HttpStatusCode.OK, successBody)

            client(engine).fetchPassage("NKJV", "GEN.1.1-GEN.2.25")

            assertThat(engine.requestHistory).hasSize(1)
            assertThat(
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            ).isEqualTo("https://proxy.example/v1/passage?bible=NKJV&ref=GEN.1.1-GEN.2.25")
            assertThat(
                engine.requestHistory
                    .single()
                    .method.value,
            ).isEqualTo("GET")
            assertThat(engine.requestHistory.single().headers["Accept"]).isEqualTo("application/json")
        }

    /** A single-verse ref is the other shape the resolver produces. */
    @Test
    fun `a single point ref produces the same url shape`() =
        runTest {
            val engine = engineReturning(HttpStatusCode.OK, successBody)

            client(engine).fetchPassage("NASB", "PSA.23.1")

            assertThat(
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            ).isEqualTo("https://proxy.example/v1/passage?bible=NASB&ref=PSA.23.1")
        }

    @Test
    fun `the app check header is sent when a token is available`() =
        runTest {
            val engine = engineReturning(HttpStatusCode.OK, successBody)

            client(engine, token = "attestation-token").fetchPassage("NKJV", "GEN.1.1")

            assertThat(engine.requestHistory.single().headers["X-Firebase-AppCheck"])
                .isEqualTo("attestation-token")
        }

    /**
     * The shipped state: App Check is not configured, the provider returns null, and the header must
     * be **absent** rather than sent empty. A blank header against a `deny` policy is a rejection.
     */
    @Test
    fun `the app check header is omitted when there is no token`() =
        runTest {
            val engine = engineReturning(HttpStatusCode.OK, successBody)

            client(engine, token = null).fetchPassage("NKJV", "GEN.1.1")

            assertThat(engine.requestHistory.single().headers["X-Firebase-AppCheck"]).isNull()
        }

    /** A broken token supplier must degrade to "no header", never take the fetch down with it. */
    @Test
    fun `a throwing token provider still fetches without the header`() =
        runTest {
            val engine = engineReturning(HttpStatusCode.OK, successBody)

            val result =
                client(engine, tokenProvider = { error("app check unavailable") })
                    .fetchPassage("NKJV", "GEN.1.1")

            assertThat(engine.requestHistory).hasSize(1)
            assertThat(engine.requestHistory.single().headers["X-Firebase-AppCheck"]).isNull()
            assertThat(result).isInstanceOf(PassageResult.Success::class)
        }

    // --- The status-code mapping. Product behaviour, not plumbing. ---

    @Test
    fun `200 parses the passage`() =
        runTest {
            val result = client(engineReturning(HttpStatusCode.OK, successBody)).fetchPassage("NKJV", "GEN.1")

            assertThat(result).isInstanceOf(PassageResult.Success::class)
            val passage = (result as PassageResult.Success).passage
            assertThat(passage.reference).isEqualTo("Genesis 1")
            assertThat(passage.copyright).isEqualTo("NKJV (c) Thomas Nelson")
            assertThat(passage.fumsToken).isEqualTo("fums-1")
            assertThat(passage.content).hasSize(1)
            assertThat(
                passage.content[0]
                    .jsonObject["name"]
                    ?.jsonPrimitive
                    ?.content,
            ).isEqualTo("para")
        }

    /**
     * `404` is NOT a failure: the passage genuinely does not exist in that translation - NASB
     * Matthew 17:21. The resolver turns this into an honest empty result **in the requested
     * version**, with no banner. Mapping it to `Unavailable` would silently show KJV instead.
     */
    @Test
    fun `404 is NotFound and not Unavailable`() =
        runTest {
            val result = client(engineReturning(HttpStatusCode.NotFound)).fetchPassage("NASB", "MAT.17.21")

            assertThat(result).isEqualTo(PassageResult.NotFound)
        }

    @Test
    fun `every other status degrades to Unavailable`() =
        runTest {
            val statuses =
                listOf(
                    HttpStatusCode.Unauthorized, // App Check rejected
                    HttpStatusCode.Forbidden,
                    HttpStatusCode.TooManyRequests,
                    HttpStatusCode.InternalServerError,
                    HttpStatusCode.ServiceUnavailable, // budget guard
                    HttpStatusCode.BadGateway,
                    HttpStatusCode.NoContent,
                    HttpStatusCode.MovedPermanently,
                )
            for (status in statuses) {
                val result = client(engineReturning(status, "body")).fetchPassage("NKJV", "GEN.1")
                assertThat(result, name = "HTTP ${status.value}").isEqualTo(PassageResult.Unavailable)
            }
        }

    // --- The swallow-everything policy: the thing a Ktor rewrite is most likely to break. ---

    /**
     * **Ktor throws where `HttpURLConnection` returned a code.** A transport failure must still
     * arrive at the reader as `Unavailable` - the KJV banner - and never as an exception.
     */
    @Test
    fun `a transport failure becomes Unavailable rather than propagating`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("connection reset") }

            val result = client(engine).fetchPassage("NKJV", "GEN.1")

            assertThat(result).isEqualTo(PassageResult.Unavailable)
        }

    @Test
    fun `malformed json becomes Unavailable`() =
        runTest {
            val result =
                client(engineReturning(HttpStatusCode.OK, "<html>proxy error</html>"))
                    .fetchPassage("NKJV", "GEN.1")

            assertThat(result).isEqualTo(PassageResult.Unavailable)
        }

    @Test
    fun `an empty 200 body becomes Unavailable`() =
        runTest {
            val result = client(engineReturning(HttpStatusCode.OK, "")).fetchPassage("NKJV", "GEN.1")

            assertThat(result).isEqualTo(PassageResult.Unavailable)
        }

    /** A JSON body missing optional fields is a success with nulls - not a degrade. */
    @Test
    fun `a null content field parses to an empty content array`() =
        runTest {
            val body = """{"reference":null,"content":null,"copyright":null,"fumsToken":null}"""

            val result = client(engineReturning(HttpStatusCode.OK, body)).fetchPassage("NKJV", "GEN.1")

            assertThat(result).isInstanceOf(PassageResult.Success::class)
            val passage = (result as PassageResult.Success).passage
            assertThat(passage.reference).isNull()
            assertThat(passage.copyright).isNull()
            assertThat(passage.fumsToken).isNull()
            assertThat(passage.content).hasSize(0)
        }

    /**
     * `ignoreUnknownKeys` in spirit: the proxy adding a field must not degrade a live install to the
     * KJV banner. Hand-rolled `parseToJsonElement` reads the four fields it wants and ignores the
     * rest - `ContentNegotiation` was deliberately NOT added in p1-03 because it would change this.
     */
    @Test
    fun `an unknown field in the response does not degrade the fetch`() =
        runTest {
            val body = """{"reference":"Genesis 1","content":[],"copyright":"c","fumsToken":"f","newField":42}"""

            val result = client(engineReturning(HttpStatusCode.OK, body)).fetchPassage("NKJV", "GEN.1")

            assertThat(result).isInstanceOf(PassageResult.Success::class)
        }
}
