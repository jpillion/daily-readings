package com.jpillion.dailyreadingplanner.bible.data

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VE-T0 — pins the startup-hook WIRING (the pure compare/delete is already pinned by
 * [BibleAssetVersionTest]). Confirms the gate reads the stored version, deletes the real
 * Room database file for the named DB when the constant is newer, and persists the new version;
 * and is a no-op once the version is current (no spurious delete, no spurious write).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BibleAssetGateTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

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
        val gate = BibleAssetGate(context, store)
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
        val gate = BibleAssetGate(context, store)
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
        val gate = BibleAssetGate(context, store)
        val dbFile = context.getDatabasePath("bible.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("old asset copy")

        val recopied = gate.ensureUpToDate("bible.db")

        assertThat(recopied).isTrue()
        assertThat(dbFile.exists()).isFalse()
        assertThat(store.writes).containsExactly(BibleAssetVersion.ASSET_CONTENT_VERSION)
    }
}
