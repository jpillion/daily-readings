package com.jpillion.dailyreadingplanner.bible.data

import java.io.File

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
        databaseFile: File,
        stored: Int?,
        constant: Int = ASSET_CONTENT_VERSION,
        deleteFiles: (File) -> Unit = ::deleteDatabaseFiles,
        persist: (Int) -> Unit,
    ): Boolean {
        if (!shouldRecopy(constant, stored)) return false
        deleteFiles(databaseFile)
        persist(constant)
        return true
    }

    /** Deletes the copied Room DB and its WAL/SHM sidecars so createFromAsset re-copies. */
    fun deleteDatabaseFiles(databaseFile: File) {
        listOf(databaseFile.path, "${databaseFile.path}-wal", "${databaseFile.path}-shm")
            .forEach { File(it).delete() }
    }
}
