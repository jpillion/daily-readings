package com.jpillion.dailyreadingplanner.bible.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** One passage as the proxy returns it. [content] is the raw USX tree for [UsxTransformer]. */
data class PassageResponse(
    val reference: String?,
    val content: JsonArray,
    val copyright: String?,
    val fumsToken: String?,
)

/** Why a fetch failed. The caller only needs "can I retry/degrade", not an HTTP code. */
sealed interface PassageResult {
    data class Success(val passage: PassageResponse) : PassageResult

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
 * `HttpURLConnection` implementation — deliberately no OkHttp/Retrofit. The call shape is one
 * authenticated GET returning JSON, and this repo has held "zero net-new runtime deps" through
 * nearly every sprint with a 12 MB bundle gate to protect.
 *
 * The App Check token is supplied per-call by [appCheckTokenProvider]; when App Check is not yet
 * configured it returns null and the header is omitted. That is the only thing standing between
 * this and a working fetch once the proxy's attestation policy allows it.
 */
class HttpBibleApiClient(
        private val baseUrl: String,
        private val appCheckTokenProvider: suspend () -> String?,
    ) : BibleApiClient {
        override suspend fun fetchPassage(
            versionCode: String,
            ref: String,
        ): PassageResult =
            withContext(Dispatchers.IO) {
                val token = runCatching { appCheckTokenProvider() }.getOrNull()
                val url = URL("$baseUrl/v1/passage?bible=$versionCode&ref=$ref")
                var conn: HttpURLConnection? = null
                try {
                    conn =
                        (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            setRequestProperty("Accept", "application/json")
                            if (token != null) setRequestProperty("X-Firebase-AppCheck", token)
                        }
                    when (val status = conn.responseCode) {
                        HttpURLConnection.HTTP_OK -> parse(conn.inputStream.readAllText())
                        HttpURLConnection.HTTP_NOT_FOUND -> PassageResult.NotFound
                        // 401/403 (attestation), 503 (budget), 5xx, anything else: degrade, do not
                        // surface an error the reader cannot act on.
                        else -> PassageResult.Unavailable.also { logUnexpected(status) }
                    }
                } catch (e: Exception) {
                    PassageResult.Unavailable
                } finally {
                    conn?.disconnect()
                }
            }

        private fun parse(body: String): PassageResult =
            runCatching {
                val root = JSON.parseToJsonElement(body).jsonObject
                PassageResult.Success(
                    PassageResponse(
                        reference = root.str("reference"),
                        content = root["content"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonArray
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

        private fun java.io.InputStream.readAllText(): String =
            bufferedReader().use(BufferedReader::readText)

        private companion object {
            const val CONNECT_TIMEOUT_MS = 10_000
            const val READ_TIMEOUT_MS = 15_000
            val JSON = Json { ignoreUnknownKeys = true }
        }
    }
