package com.jpillion.dailyreadingplanner.bible.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One passage as the proxy returns it. [content] is the raw USX tree for [UsxTransformer]. */
data class PassageResponse(
    val reference: String?,
    val content: JsonArray,
    val copyright: String?,
    val fumsToken: String?,
)

/** Why a fetch failed. The caller only needs "can I retry/degrade", not an HTTP code. */
sealed interface PassageResult {
    data class Success(
        val passage: PassageResponse,
    ) : PassageResult

    /** Offline, timeout, 5xx, budget exhausted — degrade per D-OT-2 and try again later. */
    data object Unavailable : PassageResult

    /** The passage genuinely does not exist in this version (e.g. NASB Matthew 17:21). */
    data object NotFound : PassageResult
}

/** Seam so the resolver and its tests never touch a socket. */
interface BibleApiClient {
    suspend fun fetchPassage(
        versionCode: String,
        ref: String,
    ): PassageResult
}

/**
 * Ktor implementation of the one authenticated GET this app makes.
 *
 * **This was `HttpURLConnection` until p1-03, and the KDoc used to say so** — "deliberately no
 * OkHttp/Retrofit… this repo has held zero net-new runtime deps through nearly every sprint with a
 * 12 MB bundle gate to protect." That judgement was right for an Android-only app and is recorded
 * here rather than deleted. **Ktor replaces it on the drift argument, not the convenience one**
 * (ADR-0014): `java.net.HttpURLConnection` does not exist on Kotlin/Native, and the status-code
 * mapping below is **product behaviour** — it drives the D-OT-2 offline fallback and the "Unable to
 * download NKJV, displaying KJV" banner. Duplicating it in two platform actuals would give it two
 * places to drift, with a silent failure mode.
 *
 * The dependency cost was kept honest: the engine is **`ktor-client-android` (~26 KB), not
 * `ktor-client-okhttp` (~927 KB)** — dependency-contract R3. `ktor-client-android` IS the
 * `HttpURLConnection`-backed engine, so this change swaps the API without swapping the transport.
 * The engine is supplied at the DI boundary ([httpClient]) rather than constructed here, so adding
 * a Darwin engine in Phase 2 does not reopen this file.
 *
 * JSON is still parsed **by hand** with [Json.parseToJsonElement]. `ContentNegotiation` was
 * deliberately not added: it would change parse semantics — and therefore the D-OT-2 degradation
 * behaviour — inside a release whose entire scope is transport.
 *
 * The App Check token is supplied per-call by [appCheckTokenProvider]; when App Check is not yet
 * configured it returns null and the header is omitted. That is the only thing standing between
 * this and a working fetch once the proxy's attestation policy allows it.
 */
class HttpBibleApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val appCheckTokenProvider: suspend () -> String?,
    private val ioDispatcher: CoroutineDispatcher,
) : BibleApiClient {
    override suspend fun fetchPassage(
        versionCode: String,
        ref: String,
    ): PassageResult =
        withContext(ioDispatcher) {
            val token = runCatching { appCheckTokenProvider() }.getOrNull()
            // Concatenated, not built through Ktor's parameter API, so the wire bytes and the
            // parameter ORDER are exactly what shipped. `versionCode` and `ref` are catalog-derived
            // ([A-Z0-9.-] only), so there is nothing here to encode.
            val url = "$baseUrl/v1/passage?bible=$versionCode&ref=$ref"
            try {
                val response =
                    httpClient.get(url) {
                        header("Accept", "application/json")
                        if (token != null) header("X-Firebase-AppCheck", token)
                        timeout {
                            connectTimeoutMillis = CONNECT_TIMEOUT_MS
                            socketTimeoutMillis = READ_TIMEOUT_MS
                        }
                    }
                when (val status = response.status.value) {
                    HTTP_OK -> parse(response.bodyAsText())
                    HTTP_NOT_FOUND -> PassageResult.NotFound
                    // 401/403 (attestation), 503 (budget), 5xx, anything else: degrade, do not
                    // surface an error the reader cannot act on.
                    else -> PassageResult.Unavailable.also { logUnexpected(status) }
                }
            } catch (e: Exception) {
                // Load-bearing, and the thing a Ktor rewrite is most likely to break by accident:
                // Ktor THROWS where HttpURLConnection returned a code. Timeout, DNS, TLS, a
                // malformed body, cancellation — every one of them degrades to the KJV banner
                // rather than reaching the reader as an exception.
                PassageResult.Unavailable
            }
        }

    private fun parse(body: String): PassageResult =
        runCatching {
            val root = JSON.parseToJsonElement(body).jsonObject
            PassageResult.Success(
                PassageResponse(
                    reference = root.str("reference"),
                    content =
                        root["content"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonArray
                            ?: JsonArray(emptyList()),
                    copyright = root.str("copyright"),
                    fumsToken = root.str("fumsToken"),
                ),
            )
        }.getOrElse { PassageResult.Unavailable }

    private fun JsonObject.str(name: String): String? =
        runCatching { this[name]?.jsonPrimitive?.contentOrNullSafe() }.getOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content

    private fun logUnexpected(status: Int) {
        android.util.Log.w("BibleApiClient", "passage fetch degraded status=$status")
    }

    private companion object {
        /**
         * Kept as plain ints rather than `HttpStatusCode` so the mapping stays a pure function of a
         * status number — the shape ADR-0014 names as the fallback if Ktor is ever backed out.
         */
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 15_000L
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
