package com.jpillion.dailyreadingplanner.bible.data

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * VA-T7 / ESpec-v3 §4.3, D-V3-8 — asset content-version + re-copy-on-update.
 *
 * `createFromAsset` copies the bundled bible.db exactly once and never again while the copied
 * file exists, so a shipped text correction would otherwise never reach existing users. We bump
 * [ASSET_CONTENT_VERSION] whenever bible.db content changes; on startup (OFF the main thread)
 * [ensureCurrent] compares it against the value persisted in the DataStore SettingsRepository.
 * If the constant is newer, it deletes the copied bible.db / -wal / -shm from the Room database
 * path BEFORE the Room builder runs — forcing createFromAsset to re-copy — then persists the new
 * version. This is a content-driven re-copy, NOT a Room migration (the schema never changes for a
 * text correction).
 *
 * ADR-0007 A3.3 raises the stakes on the delete: Room restamps the copied `bible.db` on first
 * open, so every shipped device is thereafter in the state where the schema hash IS compared, and
 * this deletion is the ONLY route by which a corrected asset reaches an existing install. Its
 * failure mode is silence — nothing breaks, users simply keep the old text — which is why the
 * `-wal`/`-shm` sidecars are enumerated explicitly rather than left to a directory sweep.
 *
 * The compare-and-delete logic is a pure, JVM-testable function ([shouldRecopy] / [ensureCurrent]
 * with injected seams); the DataStore read/write is supplied by the caller (di/BibleModule).
 *
 * Converse rule (enforced by this design): because the asset DB is wiped on every content bump,
 * NO user-writable data may ever live in bible.db — all user data lives in ProgressDatabase.
 */
object BibleAssetVersion {
    /**
     * The content version of the committed `assets/bible/bible.db`. Start = 1. Bump this (and
     * only this) whenever `tools/build_bible_db.py` re-derives a changed asset, so existing
     * installs re-copy on next launch.
     */
    const val ASSET_CONTENT_VERSION: Int = 1

    /** Newer constant than what's stored ⇒ re-copy. Equal ⇒ no-op. Never-stored (null) ⇒ copy. */
    fun shouldRecopy(
        constant: Int,
        stored: Int?,
    ): Boolean = stored == null || constant > stored

    /**
     * If the constant is newer than [stored], delete the copied DB files at [databaseFile] so the
     * next Room build re-copies from the asset, then invoke [persist] with the new version.
     * Returns true if a re-copy was triggered. Pure orchestration: the file delete and the
     * version persistence are the only side effects, both via injected seams.
     */
    fun ensureCurrent(
        fileSystem: FileSystem,
        databaseFile: Path,
        stored: Int?,
        constant: Int = ASSET_CONTENT_VERSION,
        deleteFiles: (Path) -> Unit = { deleteDatabaseFiles(fileSystem, it) },
        persist: (Int) -> Unit,
    ): Boolean {
        if (!shouldRecopy(constant, stored)) return false
        deleteFiles(databaseFile)
        persist(constant)
        return true
    }

    /**
     * Deletes the copied Room DB and its WAL/SHM sidecars so createFromAsset re-copies.
     *
     * `mustExist = false` is the okio spelling of the `File.delete()` this replaced, which
     * returned false rather than throwing when the file was absent — the fresh-install case,
     * where the copied DB does not exist yet, must stay a silent no-op.
     */
    fun deleteDatabaseFiles(
        fileSystem: FileSystem,
        databaseFile: Path,
    ) {
        listOf(databaseFile, "$databaseFile-wal".toPath(), "$databaseFile-shm".toPath())
            .forEach { fileSystem.delete(it, mustExist = false) }
    }
}
