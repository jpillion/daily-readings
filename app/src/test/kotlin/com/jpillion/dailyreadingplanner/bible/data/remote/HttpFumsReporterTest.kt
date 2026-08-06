package com.jpillion.dailyreadingplanner.bible.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * D-OT-9 — FUMS is a **licence obligation** for showing NKJV/NASB, so these pin the contract rather
 * than the transport: the documented manual form is
 * `GET https://fums.api.bible/f3?t=<fumsToken>&dId=<deviceId>&sId=<sessionId>`
 * (https://docs.api.bible/guides/fair-use/).
 *
 * The socket itself is NOT exercised — see the sprint handoff. What is pinned here is everything a
 * mistake would realistically break: the ids, their lifetimes, and that a failure can never take the
 * reader down with it.
 */
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

    /**
     * A reporting failure must never surface to the reader. The endpoint is unreachable in tests, so
     * this genuinely exercises the failure path: the call must return normally, not throw.
     */
    @Test
    fun `a failed report never throws`() =
        runTest {
            val reporter = HttpFumsReporter(RecordingIdentity(), StandardTestDispatcher(testScheduler))

            reporter.report("token-abc") // unreachable host -> swallowed

            // Reaching here at all is the assertion; state it so the intent is explicit.
            assertThat(true).isTrue()
        }

    /**
     * An empty token is not reportable — sending one would be a malformed request for every verse
     * displayed, and the identity must not even be read.
     */
    @Test
    fun `a blank token is not reported`() =
        runTest {
            val identity = RecordingIdentity()
            val reporter = HttpFumsReporter(identity, StandardTestDispatcher(testScheduler))

            reporter.report("")
            reporter.report("   ")

            assertThat(identity.deviceReads).isEqualTo(0)
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
            // A separate instance is a separate session ("regenerated per session").
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

            assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }
}
