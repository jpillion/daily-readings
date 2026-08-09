package com.jpillion.dailyreadingplanner.bible.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** VA-T7 — pins the re-copy compare logic (D-V3-8). Mutations: flipping the comparison or
 *  skipping the delete must be killed. */
class BibleAssetVersionTest {
    @get:Rule
    val temp = TemporaryFolder()

    /**
     * Records every path handed to `delete`, over a REAL filesystem so the deletes really happen.
     * okio's `FakeFileSystem` lives in a separate artifact this project does not depend on, and
     * `ForwardingFileSystem` ships in okio core — so this needs no new dependency and still gives
     * an exact, assertable record of which files the code asked to remove.
     */
    private class RecordingFileSystem(
        delegate: FileSystem,
    ) : ForwardingFileSystem(delegate) {
        val deleted = mutableListOf<Path>()

        override fun delete(
            path: Path,
            mustExist: Boolean,
        ) {
            deleted += path
            super.delete(path, mustExist)
        }
    }

    @Test
    fun `newer constant triggers recopy`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 2, stored = 1)).isTrue()
    }

    @Test
    fun `equal constant is a no-op`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = 1)).isFalse()
    }

    @Test
    fun `older constant never recopies`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = 2)).isFalse()
    }

    @Test
    fun `never-stored value forces an initial copy`() {
        assertThat(BibleAssetVersion.shouldRecopy(constant = 1, stored = null)).isTrue()
    }

    @Test
    fun `ensureCurrent deletes files and persists when newer`() {
        val deleted = mutableListOf<Path>()
        var persisted: Int? = null
        val recopied =
            BibleAssetVersion.ensureCurrent(
                fileSystem = FileSystem.SYSTEM,
                databaseFile = "/tmp/bible.db".toPath(),
                stored = 1,
                constant = 2,
                deleteFiles = { deleted += it },
                persist = { persisted = it },
            )
        assertThat(recopied).isTrue()
        assertThat(deleted).containsExactly("/tmp/bible.db".toPath())
        assertThat(persisted).isEqualTo(2)
    }

    @Test
    fun `ensureCurrent does nothing when current`() {
        var touched = false
        val recopied =
            BibleAssetVersion.ensureCurrent(
                fileSystem = FileSystem.SYSTEM,
                databaseFile = "/tmp/bible.db".toPath(),
                stored = 5,
                constant = 5,
                deleteFiles = { touched = true },
                persist = { touched = true },
            )
        assertThat(recopied).isFalse()
        assertThat(touched).isFalse()
    }

    @Test
    fun `starting asset content version is 1`() {
        assertThat(BibleAssetVersion.ASSET_CONTENT_VERSION).isEqualTo(1)
    }

    /**
     * THE measurement for D-V3-8, and the reason it is a count and three names rather than
     * "the file is gone".
     *
     * A corrected `bible.db` reaches an existing install ONLY by this deletion, and ADR-0007 A3.3
     * makes that sharper: Room restamps the copied DB on first open, so a shipped device is always
     * in the state where the sidecars hold committed pages. Delete `bible.db` and leave a `-wal`
     * behind and SQLite can recover the OLD content into the freshly copied file — the correction
     * is silently lost, nothing throws, and no user-visible symptom appears until someone compares
     * verse text. Asserting only on `bible.db` is exactly the blind spot that would let that ship.
     */
    @Test
    fun `deleteDatabaseFiles removes the database AND both sidecars - all three by name`() {
        val root = temp.newFolder("databases").toOkioPath()
        val db = root / "bible.db"
        val wal = root / "bible.db-wal"
        val shm = root / "bible.db-shm"
        val fs = RecordingFileSystem(FileSystem.SYSTEM)
        listOf(db, wal, shm).forEach { fs.write(it) { writeUtf8("stale") } }

        BibleAssetVersion.deleteDatabaseFiles(fs, db)

        assertThat(fs.deleted).containsExactly(db, wal, shm)
        assertThat(fs.exists(db)).isFalse()
        assertThat(fs.exists(wal)).isFalse()
        assertThat(fs.exists(shm)).isFalse()
    }

    /**
     * The fresh-install case. `java.io.File.delete()` returned false for an absent file; okio's
     * `delete` THROWS unless `mustExist = false`. Getting that wrong would crash the very first
     * launch on a device, where the copied DB does not exist yet — so it is pinned, not assumed.
     */
    @Test
    fun `deleteDatabaseFiles is a silent no-op when nothing has been copied yet`() {
        val root = temp.newFolder("empty").toOkioPath()

        BibleAssetVersion.deleteDatabaseFiles(FileSystem.SYSTEM, root / "bible.db")
    }

    /**
     * The default `deleteFiles` seam is the one production actually uses — every other
     * `ensureCurrent` test above injects a fake and therefore proves nothing about real deletion.
     * This drives the DEFAULT argument, so the sidecar rule is reachable from the entry point the
     * app calls.
     */
    @Test
    fun `ensureCurrent default delete path removes the sidecars too`() {
        val root = temp.newFolder("default-path").toOkioPath()
        val db = root / "bible.db"
        val fs = RecordingFileSystem(FileSystem.SYSTEM)
        listOf(db, root / "bible.db-wal", root / "bible.db-shm").forEach { fs.write(it) { writeUtf8("stale") } }

        val recopied =
            BibleAssetVersion.ensureCurrent(
                fileSystem = fs,
                databaseFile = db,
                stored = 1,
                constant = 2,
                persist = { },
            )

        assertThat(recopied).isTrue()
        assertThat(fs.deleted).contains(root / "bible.db-wal")
        assertThat(fs.deleted).contains(root / "bible.db-shm")
        assertThat(fs.deleted.size).isEqualTo(3)
    }
}
