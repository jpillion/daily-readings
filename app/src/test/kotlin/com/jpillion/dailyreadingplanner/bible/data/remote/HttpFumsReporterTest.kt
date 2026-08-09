package com.jpillion.dailyreadingplanner.bible.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * D-OT-9 — FUMS is a **licence obligation** for showing NKJV/NASB, so these pin the contract: the
 * documented manual form is `GET https://fums.api.bible/f3?t=<fumsToken>&dId=<deviceId>&sId=<sessionId>`
 * (https://docs.api.bible/guides/fair-use/).
 *
 * **p1-03 changed what these can prove, and that is the point.** Before Ktor there was no way to
 * see the request without a socket, so the URL — the entire licence contract — was pinned by
 * nothing at all, and the "a failed report never throws" test depended on `fums.api.bible` being
 * unreachable from the build machine. With `MockEngine` the URL is asserted directly, offline and
 * deterministically, and **the assertions cannot pass unless the request genuinely goes through the
 * Ktor client** — MockEngine only ever sees requests Ktor dispatches to it.
 *
 * **Robolectric, and only because of `android.util.Log`.** The swallow path logs, and a plain JVM
 * unit test throws `Method w in android.util.Log not mocked`. That is precisely why the previous
 * `a failed report never throws` could only pass on a machine where the request **succeeded** —
 * i.e. by making a real HTTP call to a live licence-reporting endpoint from the unit suite. The
 * swallow policy it claimed to prove was never exercised. This runner removes both problems, and
 * becomes unnecessary when ADR-0014's `Logger` seam replaces the three `android.util.Log` sites.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HttpFumsReporterTest {
    private class RecordingIdentity(
        private val device: String = "device-1",
        private val session: String = "session-1",
    ) : FumsIdentity {
        var deviceReads = 0

        override suspend fun deviceId(): String {
            deviceReads++
            return device
        }

        override fun sessionId(): String = session
    }

    private fun mockClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(HttpTimeout)
        }

    private fun okEngine(): MockEngine = MockEngine { respond("ignored body", HttpStatusCode.OK) }

    /**
     * The licence contract, character for character: endpoint, all three parameters, and their
     * ORDER. A dropped or reordered parameter is a malformed usage report to a publisher.
     */
    @Test
    fun `the encoded url form is pinned exactly`() =
        runTest {
            val engine = okEngine()
            val reporter =
                HttpFumsReporter(mockClient(engine), RecordingIdentity(), StandardTestDispatcher(testScheduler))

            reporter.report("token-abc")

            assertThat(engine.requestHistory).hasSize(1)
            assertThat(
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            ).isEqualTo("https://fums.api.bible/f3?t=token-abc&dId=device-1&sId=session-1")
            assertThat(
                engine.requestHistory
                    .single()
                    .method.value,
            ).isEqualTo("GET")
        }

    /** Real ids: UUIDs and an opaque base64-family token. Nothing here should be re-encoded. */
    @Test
    fun `realistic ids and tokens pass through the encoder unchanged`() =
        runTest {
            val engine = okEngine()
            val device = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
            val session = "9a8b7c6d-5e4f-4321-8abc-def012345678"
            val reporter =
                HttpFumsReporter(
                    mockClient(engine),
                    RecordingIdentity(device, session),
                    StandardTestDispatcher(testScheduler),
                )

            reporter.report("eyJhbGciOiJIUzI1NiJ9.abc-DEF_123")

            assertThat(
                engine.requestHistory
                    .single()
                    .url
                    .toString(),
            ).isEqualTo("https://fums.api.bible/f3?t=eyJhbGciOiJIUzI1NiJ9.abc-DEF_123&dId=$device&sId=$session")
        }

    /**
     * ADR-0014 A1 assigns Ktor's encoder to this file and the in-house `PercentEncoder` to
     * `ProviderUrlBuilder`. The two are not identical, and this measures exactly where they differ
     * so the difference can never become invisible: **space, tilde and asterisk**. None can occur in
     * a generated UUID or a base64-family token, which is why the divergence is acceptable here and
     * would NOT be acceptable in `ProviderUrlBuilder`.
     */
    @Test
    fun `encoding diverges from form encoding only on characters these values cannot contain`() {
        // Ktor's encoder - what this file now uses.
        assertThat(" ".encodeURLParameter()).isEqualTo("%20")
        assertThat("~".encodeURLParameter()).isEqualTo("~")
        assertThat("*".encodeURLParameter()).isEqualTo("%2A")

        // Form encoding - what `java.net.URLEncoder` did, still required by ProviderUrlBuilder.
        assertThat(
            com.jpillion.dailyreadingplanner.data.reference.PercentEncoder
                .encode(" "),
        ).isEqualTo("+")
        assertThat(
            com.jpillion.dailyreadingplanner.data.reference.PercentEncoder
                .encode("~"),
        ).isEqualTo("%7E")
        assertThat(
            com.jpillion.dailyreadingplanner.data.reference.PercentEncoder
                .encode("*"),
        ).isEqualTo("*")

        // Everything a real value can contain agrees.
        for (value in listOf("token-abc", "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "a.b_c-D9", "a+b/c=")) {
            assertThat(value.encodeURLParameter(), name = value).isEqualTo(
                com.jpillion.dailyreadingplanner.data.reference.PercentEncoder
                    .encode(value),
            )
        }
    }

    /**
     * A reporting failure must never surface to the reader. Deterministic now: the engine throws,
     * where the old test relied on the real endpoint being unreachable from the build machine.
     */
    @Test
    fun `a failed report never throws`() =
        runTest {
            val engine = MockEngine { throw RuntimeException("network down") }
            val identity = RecordingIdentity()
            val reporter =
                HttpFumsReporter(mockClient(engine), identity, StandardTestDispatcher(testScheduler))
            var returnedNormally = false

            reporter.report("token-abc") // swallowed
            returnedNormally = true

            // The report was genuinely attempted - the identity was read - and the throw was eaten.
            assertThat(identity.deviceReads).isEqualTo(1)
            assertThat(returnedNormally).isEqualTo(true)
        }

    /** A rejected report is still a dropped report, not an error the reader can act on. */
    @Test
    fun `a non-200 response is ignored`() =
        runTest {
            val engine = MockEngine { respond("nope", HttpStatusCode.InternalServerError) }
            val reporter =
                HttpFumsReporter(mockClient(engine), RecordingIdentity(), StandardTestDispatcher(testScheduler))

            reporter.report("token-abc")

            assertThat(engine.requestHistory).hasSize(1)
        }

    /**
     * An empty token is not reportable — sending one would be a malformed request for every verse
     * displayed, and the identity must not even be read.
     */
    @Test
    fun `a blank token is not reported`() =
        runTest {
            val identity = RecordingIdentity()
            val engine = okEngine()
            val reporter = HttpFumsReporter(mockClient(engine), identity, StandardTestDispatcher(testScheduler))

            reporter.report("")
            reporter.report("   ")

            assertThat(identity.deviceReads).isEqualTo(0)
            assertThat(engine.requestHistory).hasSize(0)
        }
}

/**
 * The two anonymous ids FUMS requires. Deliberately random UUIDs: FUMS reports how much scripture
 * was read, never who read it, and this app has never collected analytics.
 */
class DataStoreFumsIdentityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.createDataStore(
        scope: CoroutineScope,
        name: String = "fums.preferences_pb",
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, name) }

    private fun identityTest(block: suspend (DataStore<Preferences>) -> Unit) =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            try {
                block(createDataStore(scope))
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `session id is stable within a process and differs between instances`() =
        identityTest { store ->
            val a = DataStoreFumsIdentity(store)
            val b = DataStoreFumsIdentity(store)

            // Stable for the lifetime of the instance — FUMS treats it as one session.
            assertThat(a.sessionId()).isEqualTo(a.sessionId())
            // A separate instance is a separate session - "regenerated per session".
            assertThat(a.sessionId()).isNotEqualTo(b.sessionId())
        }

    @Test
    fun `device id is generated once and then stable across reads`() =
        identityTest { store ->
            val identity = DataStoreFumsIdentity(store)

            val first = identity.deviceId()
            val second = identity.deviceId()

            assertThat(first).isNotEmpty()
            assertThat(second).isEqualTo(first)
        }

    /** Persistence is the point: a relaunch must report the SAME device, not look like a new one. */
    @Test
    fun `device id survives a new instance over the same store`() =
        identityTest { store ->
            val first = DataStoreFumsIdentity(store).deviceId()
            val afterRelaunch = DataStoreFumsIdentity(store).deviceId()

            assertThat(afterRelaunch).isEqualTo(first)
        }

    /** No PII: the id must be a random UUID, never a device or account identifier. */
    @Test
    fun `device id is a random uuid`() =
        identityTest { store ->
            val id = DataStoreFumsIdentity(store).deviceId()

            assertThat(id).matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        }

    /**
     * p1-03 swapped `java.util.UUID.randomUUID` for `kotlin.uuid.Uuid.random`. **The string format
     * is an external licence-reporting contract**, so pin it harder than "looks like a uuid":
     * canonical 8-4-4-4-12, **lowercase** hex, version nibble `4` and an IETF variant nibble — which
     * is character-for-character what `java.util.UUID.randomUUID().toString()` emitted.
     */
    @Test
    fun `a generated id is a canonical lowercase version 4 uuid`() =
        identityTest { store ->
            val identity = DataStoreFumsIdentity(store)
            val v4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

            assertThat(identity.deviceId(), name = "device id").matches(v4)
            assertThat(identity.sessionId(), name = "session id").matches(v4)
        }
}
