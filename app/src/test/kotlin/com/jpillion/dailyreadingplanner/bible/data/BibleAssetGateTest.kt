package com.jpillion.dailyreadingplanner.bible.data

import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jpillion.dailyreadingplanner.platform.AndroidAppFilePaths
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VE-T0 — pins the startup-hook WIRING (the pure compare/delete is already pinned by
 * [BibleAssetVersionTest]). Confirms the gate reads the stored version, deletes the real
 * Room database file for the named DB when the constant is newer, and persists the new version;
 * and is a no-op once the version is current (no spurious delete, no spurious write).
 *
 * p1-04 added the [AndroidAppFilePaths] seam under the gate. These tests deliberately keep
 * asserting against `context.getDatabasePath(...)` — the location production used BEFORE the
 * seam existed — so they fail if the seam ever resolves somewhere else. That is the
 * sprint-00F class of defect: a moved database file breaks silently at the Room open, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BibleAssetGateTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appFilePaths = AndroidAppFilePaths(context)

    private fun gate(store: BibleAssetVersionStore) = BibleAssetGate(appFilePaths, FileSystem.SYSTEM, store)

    /** In-memory [BibleAssetVersionStore]; records writes. */
    private class FakeStore(
        var stored: Int?,
    ) : BibleAssetVersionStore {
        val writes = mutableListOf<Int>()

        override suspend fun read(): Int? = stored

        override suspend fun write(version: Int) {
            writes += version
            stored = version
        }
    }

    @Test
    fun `fresh install - never stored - re-copies and persists the current version`() {
        val store = FakeStore(stored = null)
        val gate = gate(store)
        val dbFile = context.getDatabasePath("bible.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("stale copy")

        val recopied = gate.ensureUpToDate("bible.db")

        assertThat(recopied).isTrue()
        assertThat(dbFile.exists()).isFalse() // deleted so createFromAsset re-copies
        assertThat(store.writes).containsExactly(BibleAssetVersion.ASSET_CONTENT_VERSION)
    }

    @Test
    fun `current version is a no-op — no delete - no write`() {
        val store = FakeStore(stored = BibleAssetVersion.ASSET_CONTENT_VERSION)
        val gate = gate(store)
        val dbFile = context.getDatabasePath("bible.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("good copy")

        val recopied = gate.ensureUpToDate("bible.db")

        assertThat(recopied).isFalse()
        assertThat(dbFile.exists()).isTrue() // untouched
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun `older stored version triggers re-copy and records the newer constant`() {
        // Simulate an install carrying an older asset than the one now bundled.
        val store = FakeStore(stored = BibleAssetVersion.ASSET_CONTENT_VERSION - 1)
        val gate = gate(store)
        val dbFile = context.getDatabasePath("bible.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("old asset copy")

        val recopied = gate.ensureUpToDate("bible.db")

        assertThat(recopied).isTrue()
        assertThat(dbFile.exists()).isFalse()
        assertThat(store.writes).containsExactly(BibleAssetVersion.ASSET_CONTENT_VERSION)
    }

    /**
     * p1-04 — the transcription pin. The gate no longer asks the `Context` where the database
     * lives; it asks [AndroidAppFilePaths]. This asserts the two answers are the SAME FILE, which
     * is the whole claim the seam makes on Android. If it ever drifts, the symptom on a device is
     * not an exception here but `createFromAsset` copying to one place while the app reads
     * another.
     */
    @Test
    fun `the seam resolves the same database file Context getDatabasePath does`() {
        assertThat(appFilePaths.databases / "bible.db")
            .isEqualTo(context.getDatabasePath("bible.db").toOkioPath())
    }

    /**
     * The end-to-end sidecar measurement. [BibleAssetVersionTest] pins the pure function; this
     * pins that the wiring actually reaches it — a gate that deleted only `bible.db` would leave
     * a `-wal` from which SQLite can recover the OLD text into the freshly copied file, losing the
     * correction with no exception and no user-visible symptom.
     */
    @Test
    fun `a version bump deletes the wal and shm sidecars as well as the database`() {
        val store = FakeStore(stored = BibleAssetVersion.ASSET_CONTENT_VERSION - 1)
        context.getDatabasePath("bible.db").parentFile?.mkdirs()
        val db = appFilePaths.databases / "bible.db"
        val wal = appFilePaths.databases / "bible.db-wal"
        val shm = appFilePaths.databases / "bible.db-shm"
        val fs = FileSystem.SYSTEM
        fs.write(db) { writeUtf8("old asset copy") }
        fs.write(wal) { writeUtf8("uncommitted pages from the old asset") }
        fs.write(shm) { writeUtf8("shared memory index") }

        assertThat(gate(store).ensureUpToDate("bible.db")).isTrue()

        assertThat(fs.exists(db)).isFalse()
        assertThat(fs.exists(wal)).isFalse()
        assertThat(fs.exists(shm)).isFalse()
    }
}
